package cnm.prs.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.AvisDisponibiliteDto;
import cnm.prs.dto.AvisSpecifiqueRequest;
import cnm.prs.dto.DocumentFicheDto;
import cnm.prs.dto.FicheMarcheDto;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.FicheMarche;
import cnm.prs.entity.PvExamen;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.StatutDossier;
import cnm.prs.enums.StatutFicheMarche;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ChampsInvalidesException;
import cnm.prs.exception.ErrorResponse;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.FicheMarcheRepository;
import cnm.prs.repository.PvExamenRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ <strong>Avis spécifique d'appel d'offres</strong> (demande front du 2026-09-30,
 * {@code demande-backend-2026-09-30-avis-specifique.md} ; arbitrage du pilote Q1-Q5) — une fois l'examen du dossier DAO
 * terminé et le PV signé <strong>favorable</strong> ({@code FAV}), ou <strong>favorable avec réserves</strong>
 * ({@code FAVR}) après la <strong>levée des réserves</strong>, la PRMP imprime l'avis spécifique de ce DAO.
 *
 * <p><strong>Disponibilité</strong> (§B3, §B4), dans l'ordre ; la première raison qui s'applique est rendue :</p>
 * <ol>
 *   <li>{@code CATEGORIE_SANS_AVIS} — prestations intellectuelles (lettre d'invitation, lot AV-4) ;</li>
 *   <li>{@code SANS_DOSSIER} — la fiche n'a pas de dossier soumis ;</li>
 *   <li>{@code PV_NON_SIGNE} — aucun PV signé pour ce dossier ;</li>
 *   <li>{@code AVIS_NON_FAVORABLE} — PV signé {@code DEF} ou {@code NSP} ;</li>
 *   <li>{@code RESERVES_NON_LEVEES} — PV {@code FAVR} et dossier pas encore à l'un des statuts de
 *       {@link #STATUTS_RESERVES_LEVEES} ;</li>
 *   <li>{@code FICHE_NON_VALIDEE} — garde-fou : aucune version validée (un dossier soumis en suppose une).</li>
 * </ol>
 *
 * <p><strong>Rendu</strong> : sur la dernière version <strong>validée</strong> de la fiche, même si une révision est
 * ouverte ; chaque impression produit une nouvelle paire .docx / .pdf de type {@code AVIS}, rattachée à cette version,
 * avec la trace des informations de publication. Rien n'est écrit dans la fiche.</p>
 */
@Service
public class AvisSpecifiqueService {

    public static final String CODE_INDISPONIBLE = "AVIS_INDISPONIBLE";
    public static final String CATEGORIE_SANS_AVIS = "CATEGORIE_SANS_AVIS";
    public static final String SANS_DOSSIER = "SANS_DOSSIER";
    public static final String PV_NON_SIGNE = "PV_NON_SIGNE";
    public static final String AVIS_NON_FAVORABLE = "AVIS_NON_FAVORABLE";
    public static final String RESERVES_NON_LEVEES = "RESERVES_NON_LEVEES";
    public static final String FICHE_NON_VALIDEE = "FICHE_NON_VALIDEE";

    /** Journal du dossier : une impression de l'avis. */
    public static final String JOURNAL_AVIS_IMPRIME = "AVIS_SPECIFIQUE_IMPRIME";

    /**
     * Les statuts d'un dossier {@code FAVR} dont les réserves sont levées, lus sur la navette réelle : le vérificateur
     * pose {@code OBSERVATIONS_LEVEES} à la levée ; la transmission à SIGMP ({@code DECISION_TRANSMISE_SIGMP}) n'est
     * possible, pour un FAVR, qu'après elle ({@code TransmissionSigmpService}, cas 2) ; l'archivage ({@code CLOTURE})
     * n'est possible qu'après la transmission ({@code PvExamenService#archiver}). Aucun autre chemin ne mène à ces trois
     * statuts pour un FAVR.
     */
    public static final Set<String> STATUTS_RESERVES_LEVEES = Set.of(StatutDossier.OBSERVATIONS_LEVEES.name(),
            StatutDossier.DECISION_TRANSMISE_SIGMP.name(), StatutDossier.CLOTURE.name());

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final FicheMarcheService fiches;
    private final FicheMarcheRepository ficheRepository;
    private final DossierRepository dossierRepository;
    private final PvExamenRepository pvRepository;
    private final DocumentsFicheMarcheService documents;
    private final DossierIntegriteService dossierIntegrite;
    private final JournalDossierService journal;
    private final cnm.prs.repository.MarcheRepository marcheRepository;
    private final cnm.prs.repository.StatutMarcheRepository statutMarcheRepository;
    private final cnm.prs.repository.DocumentFicheMarcheRepository documentRepository;
    private final ParametreService parametres;

    public AvisSpecifiqueService(FicheMarcheService fiches, FicheMarcheRepository ficheRepository,
            DossierRepository dossierRepository, PvExamenRepository pvRepository, DocumentsFicheMarcheService documents,
            DossierIntegriteService dossierIntegrite, JournalDossierService journal,
            cnm.prs.repository.MarcheRepository marcheRepository, cnm.prs.repository.StatutMarcheRepository statutMarcheRepository,
            cnm.prs.repository.DocumentFicheMarcheRepository documentRepository, ParametreService parametres) {
        this.parametres = parametres;
        this.marcheRepository = marcheRepository;
        this.statutMarcheRepository = statutMarcheRepository;
        this.documentRepository = documentRepository;
        this.fiches = fiches;
        this.ficheRepository = ficheRepository;
        this.dossierRepository = dossierRepository;
        this.pvRepository = pvRepository;
        this.documents = documents;
        this.dossierIntegrite = dossierIntegrite;
        this.journal = journal;
    }

    /** §B4 — l'avis peut-il être imprimé ? PRMP et UGPM, au périmètre de la fiche. */
    @Transactional(readOnly = true)
    public AvisDisponibiliteDto disponibilite(Long idDmc) {
        exigerProfil();
        return evaluer(idDmc, fiches.lire(idDmc));
    }

    /**
     * §B3 — imprime l'avis : 400 nominatif si une information de publication manque ou si une date est illisible ;
     * 409 {@code AVIS_INDISPONIBLE} (raison dans {@code details.raison}) si l'avis n'est pas disponible ; sinon la paire
     * de documents produite (.docx puis .pdf).
     */
    @Transactional
    public List<DocumentFicheDto> produire(Long idDmc, AvisSpecifiqueRequest corps) {
        exigerProfil();
        Map<String, String> publication = publication(corps);
        FicheMarcheDto courante = fiches.lire(idDmc);
        AvisDisponibiliteDto dispo = evaluer(idDmc, courante);
        if (!dispo.disponible()) {
            throw new BusinessRuleException(message(dispo.raison()), CODE_INDISPONIBLE, dispo.idDossierSoumis(),
                    Map.of("raison", dispo.raison()));
        }
        dossierIntegrite.exigerMandatActif();
        FicheMarche validee = derniereValidee(idDmc);
        FicheMarcheDto etat = fiches.lireVersion(idDmc, validee.getNumeroVersion());
        LocalDateTime maintenant = LocalDateTime.now();
        cnm.prs.entity.Marche ligne = etat.getIdDetail() == null ? null : marcheRepository.findById(etat.getIdDetail()).orElse(null);
        Integer origine = ligne == null ? null : ligne.getIdLigneOrigine() != null ? ligne.getIdLigneOrigine() : ligne.getIdDetail();
        boolean premiereImpression = origine == null || documentRepository.premierAvisDeLaFiliation(origine) == null;
        Map<String, String> trace = new LinkedHashMap<>();
        trace.put("datePublication", corps.datePublication().trim());
        trace.put("jmpNumero", corps.jmpNumero() == null ? "" : corps.jmpNumero().trim());
        trace.put("jmpDate", corps.jmpDate().trim());
        trace.put("supports", corps.supports() == null ? "" : corps.supports().trim());
        // ⚠️ 2026-10-01 (§B8.3) — le compte bancaire de l'ARMP, réglé par l'Administrateur ({{PARAM.compte-dao}}).
        String compte = parametres.compteDaoTexte();
        if (compte != null) {
            publication.put(FormulairesCandidat.JETON_COMPTE_DAO, compte);
        }
        Set<Integer> ids = documents.enregistrerAvis(validee.getIdFiche(),
                documents.produireAvis(etat, publication, maintenant), maintenant,
                DocumentsFicheMarcheService.publicationJson(trace));
        lancerLigne(ligne, origine, premiereImpression, publication.get("date-publication"));
        journal.tracer(dispo.idDossierSoumis(), JOURNAL_AVIS_IMPRIME, "Avis spécifique d'appel d'offres imprimé (fiche "
                + "marché version " + validee.getNumeroVersion() + ", publication du " + publication.get("date-publication") + ")");
        List<DocumentFicheDto> produits = new ArrayList<>(documents.listerAvis(List.of(validee)).stream()
                .filter(d -> ids.contains(d.idDocument())).toList());
        produits.sort(java.util.Comparator.comparing(DocumentFicheDto::idDocument));
        return produits;
    }

    /**
     * ⚠️ 2026-09-30 (décision du pilote, demande front « Lancé à l'avis spécifique », §B2) — le statut suit la
     * <strong>publication</strong> : à l'impression, la ligne du DMC et sa filiation vivante (la copie d'une mise à jour
     * en cours) passent de {@code PREVU} (ou sans statut) à {@code LANCE}. Un statut manuel ({@code CHDP}, {@code DSS})
     * n'est pas écrasé. Journal {@code LIGNE_LANCEE} sur le plan à la première impression (ou quand une ligne passe
     * encore « Lancé ») ; une réimpression d'une ligne déjà lancée ne change rien et n'écrit rien.
     */
    private void lancerLigne(cnm.prs.entity.Marche ligne, Integer origine, boolean premiereImpression, String datePublication) {
        if (ligne == null) {
            return;
        }
        String avant = ligne.getStatut() == null ? "" : ligne.getStatut().trim();
        boolean lancees = false;
        for (cnm.prs.entity.Marche m : marcheRepository.findFiliation(origine)) {
            String s = m.getStatut() == null ? "" : m.getStatut().trim();
            if (s.isEmpty() || StatutMarcheService.CODE_DEFAUT.equalsIgnoreCase(s)) {
                if (!statutMarcheRepository.existsById(StatutMarcheService.CODE_LANCE)) {
                    // Le code doit exister pour que la ligne se ré-enregistre (normaliser refuse un code inconnu).
                    statutMarcheRepository.save(new cnm.prs.entity.StatutMarche(StatutMarcheService.CODE_LANCE, "Lancé", 11, true));
                }
                m.setStatut(StatutMarcheService.CODE_LANCE);
                marcheRepository.save(m);
                lancees = true;
            }
        }
        if (ligne.getIdDossier() == null || !premiereImpression && !lancees) {
            return;
        }
        boolean prevu = avant.isEmpty() || StatutMarcheService.CODE_DEFAUT.equalsIgnoreCase(avant);
        String detail = "Ligne " + ligne.getIdDetail() + " : avis spécifique imprimé (publication du " + datePublication
                + "), statut " + (prevu ? (avant.isEmpty() ? "(vide)" : avant) + " → " + StatutMarcheService.CODE_LANCE
                        : StatutMarcheService.CODE_LANCE.equalsIgnoreCase(avant) ? StatutMarcheService.CODE_LANCE + " conservé"
                                : avant + " conservé (statut manuel)");
        journal.tracer(ligne.getIdDossier(), JournalDossierService.LIGNE_LANCEE, detail);
    }

    private AvisDisponibiliteDto evaluer(Long idDmc, FicheMarcheDto courante) {
        Integer idDossier = courante.getIdDossierSoumis();
        if (ModelesDao.sigleAvis(courante.getCategorie()) == null) {
            return new AvisDisponibiliteDto(false, CATEGORIE_SANS_AVIS, null, null, null, idDossier);
        }
        if (idDossier == null) {
            return new AvisDisponibiliteDto(false, SANS_DOSSIER, null, null, null, null);
        }
        String statutDossier = dossierRepository.findById(idDossier).map(Dossier::getStatut).orElse(null);
        PvExamen pv = pvRepository.findSignesParDossierRows(idDossier).stream().findFirst().orElse(null);
        if (pv == null) {
            return new AvisDisponibiliteDto(false, PV_NON_SIGNE, null, null, statutDossier, idDossier);
        }
        String avis = pv.getIdAvis();
        String raison = null;
        if ("FAVR".equals(avis)) {
            raison = STATUTS_RESERVES_LEVEES.contains(statutDossier) ? null : RESERVES_NON_LEVEES;
        } else if (!"FAV".equals(avis)) {
            raison = AVIS_NON_FAVORABLE;
        }
        if (raison == null && derniereValideeOuNull(idDmc) == null) {
            raison = FICHE_NON_VALIDEE;
        }
        return new AvisDisponibiliteDto(raison == null, raison, avis, pv.getStatutPv(), statutDossier, idDossier);
    }

    private FicheMarche derniereValideeOuNull(Long idDmc) {
        return ficheRepository.findByIdDmcOrderByNumeroVersionAsc(idDmc).stream()
                .filter(f -> StatutFicheMarche.VALIDEE.name().equals(f.getStatut()))
                .reduce((a, b) -> b).orElse(null);
    }

    private FicheMarche derniereValidee(Long idDmc) {
        FicheMarche f = derniereValideeOuNull(idDmc);
        if (f == null) {
            throw new BusinessRuleException(message(FICHE_NON_VALIDEE), CODE_INDISPONIBLE, null,
                    Map.of("raison", FICHE_NON_VALIDEE));
        }
        return f;
    }

    /** Les informations de publication, contrôlées et mises en forme pour les jetons {@code {{AVIS.*}}}. */
    private static Map<String, String> publication(AvisSpecifiqueRequest corps) {
        List<ErrorResponse.FieldError> erreurs = new ArrayList<>();
        AvisSpecifiqueRequest c = corps == null ? new AvisSpecifiqueRequest(null, null, null, null) : corps;
        String datePublication = date(c.datePublication(), "datePublication", "La date de publication de l'avis", erreurs);
        // ⚠️ 2026-10-01 (§B7.5) — le numéro du JMP et les supports sont facultatifs : vides, pointillés et « et dans … » retiré.
        String jmpNumero = c.jmpNumero() == null || c.jmpNumero().isBlank() ? null : c.jmpNumero().trim();
        String jmpDate = date(c.jmpDate(), "jmpDate", "La date du Journal des Marchés Publics de l'avis général", erreurs);
        String supports = c.supports() == null || c.supports().isBlank() ? null : c.supports().trim();
        if (!erreurs.isEmpty()) {
            throw new ChampsInvalidesException(erreurs);
        }
        Map<String, String> p = new LinkedHashMap<>();
        p.put("date-publication", datePublication);
        if (jmpNumero != null) {
            p.put("jmp-numero", jmpNumero);
        }
        p.put("jmp-date", jmpDate);
        if (supports != null) {
            p.put("supports", supports);
        }
        return p;
    }

    private static String texte(String v, String champ, String libelle, List<ErrorResponse.FieldError> erreurs) {
        if (v == null || v.isBlank()) {
            erreurs.add(new ErrorResponse.FieldError(champ, libelle + " est obligatoire."));
            return null;
        }
        return v.trim();
    }

    private static String date(String v, String champ, String libelle, List<ErrorResponse.FieldError> erreurs) {
        String t = texte(v, champ, libelle, erreurs);
        if (t == null) {
            return null;
        }
        try {
            return LocalDate.parse(t).format(JOUR);
        } catch (DateTimeParseException e) {
            erreurs.add(new ErrorResponse.FieldError(champ, libelle + " doit être une date AAAA-MM-JJ (reçu « " + t + " »)."));
            return null;
        }
    }

    private static String message(String raison) {
        return switch (raison) {
            case CATEGORIE_SANS_AVIS -> "Les prestations intellectuelles n'ont pas d'avis spécifique d'appel d'offres "
                    + "(lettre d'invitation, à venir).";
            case SANS_DOSSIER -> "L'avis spécifique ne s'imprime qu'après la soumission du dossier DAO et son examen.";
            case PV_NON_SIGNE -> "L'avis spécifique ne s'imprime qu'une fois le PV d'examen du dossier signé.";
            case AVIS_NON_FAVORABLE -> "L'avis de la Commission sur ce dossier n'est pas favorable : pas d'avis spécifique.";
            case RESERVES_NON_LEVEES -> "Le PV est favorable avec réserves : l'avis spécifique s'imprime après la levée "
                    + "des réserves.";
            default -> "La fiche marché n'a aucune version validée : pas d'avis spécifique.";
        };
    }

    private static void exigerProfil() {
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        if (profil != ProfilUtilisateur.PRMP && profil != ProfilUtilisateur.UGPM) {
            throw new AccessDeniedException("L'avis spécifique s'imprime par la PRMP de la fiche ou son UGPM.");
        }
    }
}
