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

import cnm.prs.config.HorlogeRecette;
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
    /** ⚠️ 2026-10-01 (lot AV-4.1) — fournitures et travaux : un avis, pas de lettre d'invitation. */
    public static final String CATEGORIE_SANS_LETTRE = "CATEGORIE_SANS_LETTRE";
    public static final String SANS_DOSSIER = "SANS_DOSSIER";
    public static final String PV_NON_SIGNE = "PV_NON_SIGNE";
    public static final String AVIS_NON_FAVORABLE = "AVIS_NON_FAVORABLE";
    public static final String RESERVES_NON_LEVEES = "RESERVES_NON_LEVEES";
    public static final String FICHE_NON_VALIDEE = "FICHE_NON_VALIDEE";
    /** ⚠️ 2026-10-04 (soumission en ligne, lot 2, §B2.6) — remise électronique : la cérémonie des clés n'est pas close. */
    public static final String CEREMONIE_NON_CLOSE = "CEREMONIE_NON_CLOSE";
    /** ⚠️ R3c (manuel révisé, lot 1, §B3) — un appel d'offres restreint n'a pas d'avis public : lettres d'invitation (LETTRE-AOR). */
    public static final String MODE_SANS_AVIS = "MODE_SANS_AVIS";
    /** ⚠️ V118 (2026-10-15, §B2, H1) — le plan ne porte pas de publication de l'AGPM : le journal de l'avis général ne se lit nulle part. */
    public static final String PUBLICATION_AGPM_ABSENTE = PublicationAgpmService.CODE_ABSENTE;
    /** ⚠️ 2026-10-18 (« dao-electronique », Q4) — dossier payant, compte de l'ARMP non réglé par l'Administrateur. */
    public static final String COMPTE_ARMP_ABSENT = "COMPTE_ARMP_ABSENT";
    static final String CHAMP_FRAIS_DOSSIER = "B04-DS-05";

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
    /** ⚠️ 2026-10-04 (soumission en ligne, lot 2, §B2.6) — la cérémonie des clés, que la publication exige close en mode électronique. */
    private final CeremonieService ceremonie;
    /** ⚠️ R3c — le mode du plan (appel d'offres restreint : lettre AOR, pas d'avis). */
    private final ValeursPpmService valeursPpm;
    /** ⚠️ R6a (manuel révisé, lot 2, §B1) — le délai de publicité de la loi, bloquant à l'impression (409 DELAI_PUBLICITE). */
    private final DelaisPublicite delaisPublicite;
    /** ⚠️ R9a (manuel révisé, lot 2, §B4 n° 2, art. 31) — le DAO complet de la version, exigé avant toute publication. */
    private final DaoCompletService daoComplet;

    /** ⚠️ V118 (§B2, H1) — la publication de l'AGPM du plan : numéro et date du journal de l'avis général. */
    private final PublicationAgpmService publicationAgpm;
    /** ⚠️ 2026-10-18 (« dao-electronique », Q4) — le compte de l'ARMP où se versent les frais du dossier. */
    private final ParametreService parametres;

    public AvisSpecifiqueService(FicheMarcheService fiches, FicheMarcheRepository ficheRepository,
            DossierRepository dossierRepository, PvExamenRepository pvRepository, DocumentsFicheMarcheService documents,
            DossierIntegriteService dossierIntegrite, JournalDossierService journal,
            cnm.prs.repository.MarcheRepository marcheRepository, cnm.prs.repository.StatutMarcheRepository statutMarcheRepository,
            cnm.prs.repository.DocumentFicheMarcheRepository documentRepository, CeremonieService ceremonie, ValeursPpmService valeursPpm,
            DelaisPublicite delaisPublicite, DaoCompletService daoComplet, PublicationAgpmService publicationAgpm,
            ParametreService parametres) {
        this.parametres = parametres;
        this.publicationAgpm = publicationAgpm;
        this.daoComplet = daoComplet;
        this.delaisPublicite = delaisPublicite;
        this.valeursPpm = valeursPpm;
        this.ceremonie = ceremonie;
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
        FicheMarcheDto courante = fiches.lire(idDmc);
        return evaluer(idDmc, courante, false).avecDaoComplet(etatDaoComplet(idDmc))
                .avecSupportsMinimaux(supportsMinimaux(courante.getTypeMarche()));
    }

    /**
     * ⚠️ 2026-10-15 (demande front « marchés subséquents », §B0.2) — décret n° 2019-1064, art. 9 : en sus des art. 32-33 de la loi,
     * la mise en concurrence d'un contrat-cadre se publie au minimum sur ces supports. Un rappel servi à la disponibilité ; rien
     * n'est exigé à l'impression.
     */
    static final List<String> SUPPORTS_MINIMAUX_CONTRAT_CADRE = List.of(
            "Site web de l'acheteur public",
            "Site web de l'Autorité de Régulation des Marchés Publics",
            "Bureau du contrôle financier",
            "Bureau de la Chambre de Commerce",
            "Média écrit ou audiovisuel local");

    static List<String> supportsMinimaux(String typeMarche) {
        return "CONTRAT_CADRE".equals(typeMarche) ? SUPPORTS_MINIMAUX_CONTRAT_CADRE : List.of();
    }

    /**
     * ⚠️ R9a (remarque du front du 14/10) — l'état du DAO complet de la dernière version validée, annoncé par la disponibilité :
     * {@code PRODUIT}, {@code A_ASSEMBLER}, {@code SANS_ASSEMBLAGE}, ou {@code null} sans version validée. Lecture seule.
     */
    String etatDaoComplet(Long idDmc) {
        FicheMarche validee = derniereValideeOuNull(idDmc);
        if (validee == null) {
            return null;
        }
        if (documentRepository.findByIdFicheOrderByIdDocumentAsc(validee.getIdFiche()).stream()
                .anyMatch(d -> DaoCompletService.TYPE.equals(d.getType()))) {
            return "PRODUIT";
        }
        return daoComplet.actif() ? "A_ASSEMBLER" : "SANS_ASSEMBLAGE";
    }

    /**
     * §B3 — imprime l'avis : 400 nominatif si une information de publication manque ou si une date est illisible ;
     * 409 {@code AVIS_INDISPONIBLE} (raison dans {@code details.raison}) si l'avis n'est pas disponible ; sinon la paire
     * de documents produite (.docx puis .pdf).
     */
    @Transactional
    public List<DocumentFicheDto> produire(Long idDmc, AvisSpecifiqueRequest corps) {
        exigerProfil();
        // ⚠️ V118 (§B2, H1) — le numéro et la date du journal de l'avis général se lisent sur la publication déposée sur le plan.
        cnm.prs.entity.PublicationAgpm agpm = publicationAgpm.enVigueurPourDmc(idDmc).orElse(null);
        Map<String, String> publication = publication(corps, agpm);
        FicheMarcheDto courante = fiches.lire(idDmc);
        AvisDisponibiliteDto dispo = evaluer(idDmc, courante, false);
        if (!dispo.disponible()) {
            throw new BusinessRuleException(message(dispo.raison(), false), CODE_INDISPONIBLE, dispo.idDossierSoumis(),
                    Map.of("raison", dispo.raison()));
        }
        dossierIntegrite.exigerMandatActif();
        FicheMarche validee = derniereValidee(idDmc, false);
        FicheMarcheDto etat = fiches.lireVersion(idDmc, validee.getNumeroVersion());
        // ⚠️ R6a (manuel révisé, lot 2, §B1 ; arbitrage Q6) — la date de publication saisie respecte le délai de publicité de la loi.
        delaisPublicite.exigerAImpression(etat, etat.getIdDetail() == null ? null : valeursPpm.lire(etat.getIdDetail()),
                LocalDate.parse(corps.datePublication().trim()));
        exigerDaoComplet(validee, etat, dispo.idDossierSoumis());
        LocalDateTime maintenant = LocalDateTime.now(HorlogeRecette.HORLOGE);
        cnm.prs.entity.Marche ligne = ligne(etat);
        Integer origine = origine(ligne);
        boolean premiereImpression = premiereImpression(origine);
        Map<String, String> trace = new LinkedHashMap<>();
        trace.put("datePublication", corps.datePublication().trim());
        trace.put("jmpNumero", agpm.getNumero() == null ? "" : agpm.getNumero());   // ⚠️ V118 (H1) : lus sur le plan
        trace.put("jmpDate", agpm.getDateParution().toString());
        trace.put("journal", agpm.getJournal());
        trace.put("referenceAgpm", agpm.getReferenceAgpm());
        trace.put("supports", corps.supports() == null ? "" : corps.supports().trim());
        // ⚠️ 2026-10-01 (§B8.3) — le compte bancaire de l'ARMP ({{PARAM.compte-dao}}) : DocumentsFicheMarcheService.parametresDocuments.
        Set<Integer> ids = documents.enregistrerAvis(validee.getIdFiche(),
                documents.produireAvis(etat, publication, maintenant), maintenant,
                DocumentsFicheMarcheService.publicationJson(trace));
        lancerLigne(ligne, origine, premiereImpression, "avis spécifique imprimé (publication du "
                + publication.get("date-publication") + ")");
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
    /** ⚠️ R3b — un fichier rendu à la volée : nom, extension ({@code pdf} ou {@code docx}), contenu. */
    public record Telechargement(String nomFichier, String extension, byte[] contenu) {
    }

    /**
     * ⚠️ R3b (manuel révisé pour l'application, lot 1, §B3 ; 2026-10-13) — le <strong>projet</strong> d'avis spécifique (ou de
     * lettre d'invitation) rendu à la volée sur la dernière version validée, sans dossier ni PV : « PROJET » en filigrane,
     * informations de publication et destinataire en pointillés, pied d'équivalence SIGMP. Rien n'est écrit, la ligne ne passe pas
     * « Lancé ». PRMP et UGPM au périmètre de la fiche. 409 {@code AVIS_INDISPONIBLE} / {@code LETTRE_INDISPONIBLE}, raison
     * {@code CATEGORIE_SANS_AVIS} / {@code CATEGORIE_SANS_LETTRE} ou {@code FICHE_NON_VALIDEE}.
     *
     * @param format {@code docx}, ou {@code pdf} (défaut)
     */
    @Transactional(readOnly = true)
    public Telechargement projet(Long idDmc, String format, boolean lettre) {
        exigerProfil();
        FicheMarcheDto courante = fiches.lire(idDmc);
        String sansModele = sansModele(courante, lettre);
        if (sansModele != null) {
            throw new BusinessRuleException(message(sansModele, lettre), lettre ? LettreInvitationService.CODE_INDISPONIBLE : CODE_INDISPONIBLE,
                    courante.getIdDossierSoumis(), Map.of("raison", sansModele));
        }
        FicheMarche validee = derniereValidee(idDmc, lettre);
        FicheMarcheDto etat = fiches.lireVersion(idDmc, validee.getNumeroVersion());
        String extension = "docx".equalsIgnoreCase(format == null ? "" : format.trim()) ? "docx" : "pdf";
        return documents.produireProjet(etat, validee.getDateValidation()).stream().filter(p -> extension.equals(p.extension()))
                .map(p -> new Telechargement(p.nomFichier(), p.extension(), p.contenu())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Le projet n'a pas été rendu en " + extension + "."));
    }

    /**
     * ⚠️ R3c — la raison pour laquelle la fiche n'a pas ce document, ou {@code null} : {@code CATEGORIE_SANS_AVIS} (prestations
     * intellectuelles), {@code MODE_SANS_AVIS} (appel d'offres restreint), {@code CATEGORIE_SANS_LETTRE} (fournitures et travaux en
     * appel d'offres ouvert).
     */
    String sansModele(FicheMarcheDto courante, boolean lettre) {
        boolean restreint = valeursPpm.modeRestreint(courante.getIdDetail());
        if (lettre) {
            return ModelesDao.sigleLettre(courante.getCategorie(), restreint) == null ? CATEGORIE_SANS_LETTRE : null;
        }
        if (ModelesDao.sigleAvis(courante.getCategorie()) == null) {
            return CATEGORIE_SANS_AVIS;
        }
        return restreint ? MODE_SANS_AVIS : null;
    }

    /**
     * ⚠️ R3c (manuel révisé, lot 1, §B3 ; arbitrage Q3 : « mentions complètes ») — les mentions du modèle type, vérifiées sur le
     * gabarit de la fiche (AVIS-F, AVIS-T, LETTRE-PI, LETTRE-AOR) et sur le document rendu pour sa <strong>version courante</strong>
     * (brouillon ou validée) : la PRMP relit et complète avant de valider. Lecture seule. PRMP et UGPM. 409 sans version
     * enregistrée ({@code FICHE_NON_VALIDEE}) ou sans modèle ({@code CATEGORIE_SANS_AVIS}, {@code MODE_SANS_AVIS},
     * {@code CATEGORIE_SANS_LETTRE}).
     */
    @Transactional(readOnly = true)
    public cnm.prs.dto.MentionsDto mentions(Long idDmc, boolean lettre) {
        exigerProfil();
        FicheMarcheDto courante = fiches.lire(idDmc);
        String code = lettre ? LettreInvitationService.CODE_INDISPONIBLE : CODE_INDISPONIBLE;
        String sansModele = sansModele(courante, lettre);   // la catégorie et le mode se lisent au plan, avant toute version
        if (sansModele != null) {
            throw new BusinessRuleException(message(sansModele, lettre), code, courante.getIdDossierSoumis(), Map.of("raison", sansModele));
        }
        if (courante.getIdFiche() == null) {
            throw new BusinessRuleException(message(FICHE_NON_VALIDEE, lettre), code, courante.getIdDossierSoumis(), Map.of("raison", FICHE_NON_VALIDEE));
        }
        boolean restreint = valeursPpm.modeRestreint(courante.getIdDetail());
        String sigle = lettre ? ModelesDao.sigleLettre(courante.getCategorie(), restreint) : ModelesDao.sigleAvis(courante.getCategorie(), restreint);
        return MentionsPublication.verifier(sigle, documents.modele(sigle), documents.rendrePublication(courante, sigle, lettre), courante);
    }

    /** ⚠️ R9a — le DAO complet de la version validée n'est pas produit : l'avis et les lettres attendent. */
    public static final String DAO_COMPLET_ABSENT = "DAO_COMPLET_ABSENT";

    /**
     * ⚠️ R9a (manuel révisé, lot 2, §B4 n° 2 ; art. 31 : le DAO est complet avant sa publication) — à l'impression de l'avis ou des
     * lettres, le DAO complet de la version validée doit exister : s'il manque, il est assemblé sur-le-champ ({@link
     * DaoCompletService#assurer}, synchrone, ~20 s) ; un échec rend 409 {@code DAO_COMPLET_ABSENT} {@code { version }}, le rattrapage
     * périodique le reprendra. Sans assemblage actif sur ce serveur ({@code app.dao-complet.actif} faux ou hors Windows), le DAO
     * complet n'existe pour aucune fiche : la règle ne s'applique pas (les documents séparés font le DAO).
     */
    void exigerDaoComplet(FicheMarche validee, FicheMarcheDto etat, Integer idDossierSoumis) {
        if (!daoComplet.actif() || daoComplet.assurer(validee, etat)) {
            return;
        }
        throw new BusinessRuleException("Le DAO complet de la version " + validee.getNumeroVersion() + " n'a pas pu être assemblé : il se "
                + "publie complet (art. 31). Réessayez dans quelques minutes ; l'assemblage est repris automatiquement.", DAO_COMPLET_ABSENT,
                idDossierSoumis, Map.of("version", validee.getNumeroVersion()));
    }

    /** La ligne du DMC de la fiche ; {@code null} sans ligne. */
    cnm.prs.entity.Marche ligne(FicheMarcheDto etat) {
        return etat.getIdDetail() == null ? null : marcheRepository.findById(etat.getIdDetail()).orElse(null);
    }

    /** L'origine de la filiation d'une ligne (elle-même si elle n'est la copie d'aucune). */
    static Integer origine(cnm.prs.entity.Marche ligne) {
        return ligne == null ? null : ligne.getIdLigneOrigine() != null ? ligne.getIdLigneOrigine() : ligne.getIdDetail();
    }

    /**
     * Aucune publication (avis ou ⚠️ 2026-10-01 lettre d'invitation) n'a encore été imprimée pour cette filiation : la
     * première fait passer la ligne à « Lancé ».
     */
    boolean premiereImpression(Integer origine) {
        return origine == null || documentRepository.premierAvisDeLaFiliation(origine) == null;
    }

    /** @param quoi ce que dit le journal de la ligne : « avis spécifique imprimé (publication du …) », « lettres… ». */
    void lancerLigne(cnm.prs.entity.Marche ligne, Integer origine, boolean premiereImpression, String quoi) {
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
        String detail = "Ligne " + ligne.getIdDetail() + " : " + quoi + ", statut " + (prevu ? (avant.isEmpty() ? "(vide)" : avant) + " → " + StatutMarcheService.CODE_LANCE
                        : StatutMarcheService.CODE_LANCE.equalsIgnoreCase(avant) ? StatutMarcheService.CODE_LANCE + " conservé"
                                : avant + " conservé (statut manuel)");
        journal.tracer(ligne.getIdDossier(), JournalDossierService.LIGNE_LANCEE, detail);
    }

    /**
     * @param lettre ⚠️ 2026-10-01 (lot AV-4.1) — la lettre d'invitation des prestations intellectuelles : même garde que
     *               l'avis, la catégorie inversée ({@code CATEGORIE_SANS_LETTRE} pour les fournitures et les travaux)
     */
    AvisDisponibiliteDto evaluer(Long idDmc, FicheMarcheDto courante, boolean lettre) {
        Integer idDossier = courante.getIdDossierSoumis();
        String sansModele = sansModele(courante, lettre);
        if (sansModele != null) {
            return new AvisDisponibiliteDto(false, sansModele, null, null, null, idDossier);
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
        // ⚠️ 2026-10-04 (lot 2, §B2.6) — sans clés publiées, un candidat ne pourrait pas sceller : publier serait annoncer
        // une procédure impraticable. Avis et lettres d'invitation : même garde.
        if (raison == null && RemiseElectronique.electronique(courante.getCadrage()) && !ceremonie.estClose(idDmc)) {
            raison = CEREMONIE_NON_CLOSE;
        }
        // ⚠️ V118 (2026-10-15, §B2, H1) — l'avis cite le journal de l'avis général : la publication de l'AGPM est déposée sur le plan.
        if (raison == null && !lettre && publicationAgpm.enVigueurPourDmc(idDmc).isEmpty()) {
            raison = PUBLICATION_AGPM_ABSENTE;
        }
        // ⚠️ 2026-10-18 (« dao-electronique », Q4) — un dossier payant imprime le compte de l'ARMP où verser les frais : tant que
        // l'Administrateur ne l'a pas réglé, le document porterait des pointillés. Avis et lettres AOR : même garde.
        if (raison == null && payant(courante) && parametres.compteDaoTexte() == null) {
            raison = COMPTE_ARMP_ABSENT;
        }
        return new AvisDisponibiliteDto(raison == null, raison, avis, pv.getStatutPv(), statutDossier, idDossier);
    }

    /** Le dossier est payant : un montant de {@code B04-DS-05} (unique ou par lot, {@code B04-DS-05#n}) est positif. */
    static boolean payant(FicheMarcheDto fiche) {
        Map<String, String> v = fiche.getValeurs() == null ? Map.of() : fiche.getValeurs();
        return v.entrySet().stream()
                .filter(e -> e.getKey().equals(CHAMP_FRAIS_DOSSIER) || e.getKey().startsWith(CHAMP_FRAIS_DOSSIER + LotsFiche.SEPARATEUR))
                .anyMatch(e -> {
                    try {
                        return e.getValue() != null && new java.math.BigDecimal(e.getValue().trim().replace(" ", "")).signum() > 0;
                    } catch (NumberFormatException ex) {
                        return false;
                    }
                });
    }

    private FicheMarche derniereValideeOuNull(Long idDmc) {
        return ficheRepository.findByIdDmcOrderByNumeroVersionAsc(idDmc).stream()
                .filter(f -> StatutFicheMarche.VALIDEE.name().equals(f.getStatut()))
                .reduce((a, b) -> b).orElse(null);
    }

    FicheMarche derniereValidee(Long idDmc, boolean lettre) {
        FicheMarche f = derniereValideeOuNull(idDmc);
        if (f == null) {
            throw new BusinessRuleException(message(FICHE_NON_VALIDEE, lettre),
                    lettre ? LettreInvitationService.CODE_INDISPONIBLE : CODE_INDISPONIBLE, null, Map.of("raison", FICHE_NON_VALIDEE));
        }
        return f;
    }

    /** Les informations de publication, contrôlées et mises en forme pour les jetons {@code {{AVIS.*}}}. */
    private static Map<String, String> publication(AvisSpecifiqueRequest corps, cnm.prs.entity.PublicationAgpm agpm) {
        List<ErrorResponse.FieldError> erreurs = new ArrayList<>();
        AvisSpecifiqueRequest c = corps == null ? new AvisSpecifiqueRequest(null, null, null, null) : corps;
        String datePublication = date(c.datePublication(), "datePublication", "La date de publication de l'avis", erreurs);
        // ⚠️ 2026-10-01 (§B7.5) — le numéro du JMP et les supports sont facultatifs : vides, pointillés et « et dans … » retiré.
        // ⚠️ V118 (2026-10-15, §B2, H1) — numéro et date du journal lus sur la publication de l'AGPM du plan, figés : ceux du corps sont
        // ignorés ; sans publication, l'avis est indisponible (409 PUBLICATION_AGPM_ABSENTE, jugé ensuite).
        String jmpNumero = agpm == null || agpm.getNumero() == null ? null : agpm.getNumero();
        String jmpDate = agpm == null ? null : agpm.getDateParution().format(JOUR);
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

    static String texte(String v, String champ, String libelle, List<ErrorResponse.FieldError> erreurs) {
        if (v == null || v.isBlank()) {
            erreurs.add(new ErrorResponse.FieldError(champ, libelle + " est obligatoire."));
            return null;
        }
        return v.trim();
    }

    static String date(String v, String champ, String libelle, List<ErrorResponse.FieldError> erreurs) {
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

    static String message(String raison, boolean lettre) {
        String quoi = lettre ? "Les lettres d'invitation" : "L'avis spécifique";
        String accord = lettre ? "s'impriment" : "s'imprime";
        return switch (raison) {
            case CATEGORIE_SANS_AVIS -> "Les prestations intellectuelles n'ont pas d'avis spécifique d'appel d'offres : les "
                    + "candidats de la liste restreinte reçoivent une lettre d'invitation.";
            case CATEGORIE_SANS_LETTRE -> "Les fournitures et les travaux n'ont pas de lettre d'invitation : leur appel "
                    + "d'offres se publie par l'avis spécifique.";
            case SANS_DOSSIER -> quoi + " ne " + accord + " qu'après la soumission du dossier DAO et son examen.";
            case PV_NON_SIGNE -> quoi + " ne " + accord + " qu'une fois le PV d'examen du dossier signé.";
            case AVIS_NON_FAVORABLE -> "L'avis de la Commission sur ce dossier n'est pas favorable : "
                    + (lettre ? "pas de lettre d'invitation." : "pas d'avis spécifique.");
            case RESERVES_NON_LEVEES -> "Le PV est favorable avec réserves : " + quoi.toLowerCase(java.util.Locale.ROOT) + " "
                    + accord + " après la levée des réserves.";
            case CEREMONIE_NON_CLOSE -> "La cérémonie des clés n'est pas close : sans clés publiées, aucun candidat ne pourrait sceller "
                    + "son offre. " + quoi + " " + accord + " une fois la cérémonie close.";
            case MODE_SANS_AVIS -> "Un appel d'offres restreint n'a pas d'avis spécifique : les candidats de la liste restreinte "
                    + "reçoivent une lettre d'invitation à soumissionner.";
            case PUBLICATION_AGPM_ABSENTE -> "La publication de l'AGPM n'est pas déposée sur le plan de passation : l'avis spécifique cite le "
                    + "numéro et la date du journal de l'avis général, qui se lisent sur elle. Déposez-la sur le plan contrôlé.";
            case COMPTE_ARMP_ABSENT -> "Le dossier est payant et le compte bancaire de l'ARMP où verser les frais n'est pas réglé : "
                    + quoi.toLowerCase(java.util.Locale.ROOT) + " " + accord + " une fois le compte réglé par l'Administrateur.";
            default -> "La fiche marché n'a aucune version validée : " + (lettre ? "pas de lettre d'invitation." : "pas d'avis spécifique.");
        };
    }

    static void exigerProfil() {
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        if (profil != ProfilUtilisateur.PRMP && profil != ProfilUtilisateur.UGPM) {
            throw new AccessDeniedException("L'avis spécifique et les lettres d'invitation s'impriment par la PRMP de la fiche "
                    + "ou son UGPM.");
        }
    }
}
