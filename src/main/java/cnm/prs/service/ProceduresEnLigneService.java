package cnm.prs.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.DocumentFicheDto;
import cnm.prs.dto.ProcedureEnLigneDto;
import cnm.prs.entity.ChampFicheMarche;
import cnm.prs.entity.CompteCandidat;
import cnm.prs.entity.DocumentFicheMarche;
import cnm.prs.entity.Entreprise;
import cnm.prs.entity.FicheMarche;
import cnm.prs.entity.Lot;
import cnm.prs.entity.RecuDao;
import cnm.prs.entity.RetraitDao;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.CompteCandidatRepository;
import cnm.prs.repository.DocumentFicheMarcheRepository;
import cnm.prs.repository.EntrepriseRepository;
import cnm.prs.repository.FicheMarcheRepository;
import cnm.prs.repository.LotRepository;
import cnm.prs.repository.RetraitDaoRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 1c, §B8) — les <strong>procédures ouvertes en ligne</strong>
 * et le <strong>retrait du DAO</strong>.
 * <p>
 * Une procédure figure dans la liste publique si sa fiche a une version <strong>validée</strong> (la dernière), en mode
 * {@code ELECTRONIQUE} (cadrage {@code modeRemise}), <strong>lancée</strong> (un avis spécifique imprimé sur l'une de ses
 * versions : les lettres d'invitation des prestations intellectuelles, liste restreinte, n'ouvrent rien au public), dont
 * la date limite de remise n'est pas passée, et qui n'exige que la signature <strong>Simple</strong> (Q5 : la plateforme
 * ne sait pas encore faire mieux ; {@code B04-SE-05} vide vaut Simple). Lue par son identifiant, une procédure close
 * reste visible ({@code etat = CLOSE}) ; hors de ces critères, 404 — sans dire pourquoi.
 * <p>
 * Les champs sont lus sur la fiche validée, tels que les documents les impriment (champ fermé ou d'une autre forme :
 * {@code null}) : aucun paramètre interne (V50, ADR-0010). Le retrait est libre pour tout candidat connecté et journalisé
 * à chaque téléchargement ({@code t_retrait_dao}, V65) ; la PRMP de la fiche lit ce registre.
 */
@Service
public class ProceduresEnLigneService {

    static final String CHAMP_AUTORITE = "B01-AC-01";
    static final String CHAMP_HEURE_REFERENCE = "B04-SE-04";
    static final String CHAMP_SIGNATURE = "B04-SE-05";
    static final String CHAMP_FORMATS = "B04-SE-07";
    static final String CHAMP_TAILLE_FICHIER = "B04-SE-08";
    static final String CHAMP_TAILLE_OFFRE = "B04-SE-09";
    static final String CHAMP_ASSISTANCE = "B04-SE-14";
    /** ⚠️ 2026-10-04 (lot 3) — remplacement et retrait avant la date limite (OUI / NON). */
    static final String CHAMP_REMPLACEMENT = "B04-SE-10";

    public static final String A_VENIR = "A_VENIR";
    public static final String OUVERTE = "OUVERTE";
    public static final String CLOSE = "CLOSE";

    private final FicheMarcheRepository ficheRepository;
    private final FicheMarcheService fiches;
    private final DocumentsFicheMarcheService documents;
    private final DocumentFicheMarcheRepository documentRepository;
    private final LotRepository lotRepository;
    private final RetraitDaoRepository retraitRepository;
    private final CompteCandidatRepository compteRepository;
    private final EntrepriseRepository entrepriseRepository;
    private final PiecesFiche piecesFiche;
    /** ⚠️ 2026-10-05 (lot 5, §B1.3) — le besoin décide si l'offre se dépose par formulaires. */
    private final BesoinFiche besoin;
    /** ⚠️ 2026-10-06 — les reçus de frais de dossier (garde du retrait) et le compte de l'ARMP. */
    private final cnm.prs.repository.RecuDaoRepository recuRepository;
    private final ParametreService parametres;

    public ProceduresEnLigneService(FicheMarcheRepository ficheRepository, FicheMarcheService fiches,
            DocumentsFicheMarcheService documents, DocumentFicheMarcheRepository documentRepository, LotRepository lotRepository,
            RetraitDaoRepository retraitRepository, CompteCandidatRepository compteRepository,
            EntrepriseRepository entrepriseRepository, PiecesFiche piecesFiche, BesoinFiche besoin,
            cnm.prs.repository.RecuDaoRepository recuRepository, ParametreService parametres) {
        this.recuRepository = recuRepository;
        this.parametres = parametres;
        this.besoin = besoin;
        this.piecesFiche = piecesFiche;
        this.ficheRepository = ficheRepository;
        this.fiches = fiches;
        this.documents = documents;
        this.documentRepository = documentRepository;
        this.lotRepository = lotRepository;
        this.retraitRepository = retraitRepository;
        this.compteRepository = compteRepository;
        this.entrepriseRepository = entrepriseRepository;
    }

    /** La procédure lue, et sa fiche validée. */
    record Lue(ProcedureEnLigneDto dto, FicheMarche fiche, FicheMarcheService.EtatVersion etat) {
    }

    // ------------------------------------------------------------------ public

    /** La liste publique : procédures à venir ou ouvertes, la date limite la plus proche d'abord. */
    @Transactional(readOnly = true)
    public List<ProcedureEnLigneDto> lister() {
        LocalDateTime maintenant = LocalDateTime.now();
        List<ProcedureEnLigneDto> out = new ArrayList<>();
        for (FicheMarche f : ficheRepository.findDernieresValidees()) {
            // tri grossier avant la lecture complète de la fiche : le cadrage enregistré doit dire « électronique »
            if (f.getCadrage() == null || !f.getCadrage().contains(RemiseElectronique.ELECTRONIQUE)) {
                continue;
            }
            lire(f.getIdDmc(), maintenant).map(Lue::dto).filter(d -> !CLOSE.equals(d.etat())).ifPresent(out::add);
        }
        out.sort(Comparator.comparing(ProcedureEnLigneDto::dateLimite, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ProcedureEnLigneDto::idDmc));
        return out;
    }

    /** Une procédure par son identifiant : 404 hors des critères de la liste (close : lisible). */
    @Transactional(readOnly = true)
    public ProcedureEnLigneDto procedure(Long idDmc) {
        return exiger(idDmc).dto();
    }

    // ------------------------------------------------------------------ candidat

    /**
     * Les documents du DAO à retirer : ceux de la dernière version validée, sans l'avis ni les lettres d'invitation.
     * {@code code} = le nom du fichier (unique dans une version).
     */
    @Transactional(readOnly = true)
    public List<ProcedureEnLigneDto.Document> documents(Long idDmc) {
        Lue l = exiger(idDmc);
        return documents.lister(l.fiche(), l.etat().categorie()).stream()
                .map(d -> new ProcedureEnLigneDto.Document(d.nomFichier(), d.libelle(), d.version(),
                        d.tailleOctets() == null ? 0 : d.tailleOctets()))
                .toList();
    }

    /** Un document du DAO, retiré par le candidat {@code idCandidat} : le retrait est journalisé. 404 inconnu. */
    @Transactional
    public DocumentFicheMarche retirer(Long idDmc, String code, String idCandidat) {
        Lue l = exiger(idDmc);
        DocumentFicheMarche d = documentRepository.findByIdFicheOrderByIdDocumentAsc(l.fiche().getIdFiche()).stream()
                .filter(x -> !DocumentsFicheMarcheService.TYPES_PUBLICATION.contains(x.getType()))
                .filter(x -> x.getNomFichier() != null && x.getNomFichier().equals(code))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Document introuvable : " + code + "."));
        // ⚠️ 2026-10-06 (§B4) — un dossier payant ne se retire qu'avec un reçu validé de l'entreprise (au moins un lot : le dossier
        // est commun). Rien n'est inscrit au registre en cas de refus.
        RecuDao recu = null;
        if (l.dto().retraitPayant()) {
            recu = recuValide(idDmc, nifDe(idCandidat), null).orElseThrow(() -> new cnm.prs.exception.AccesReserveException(
                    "Le dossier se retire une fois le reçu du paiement des frais validé par la PRMP : déposez votre reçu, puis attendez "
                            + "sa validation.", "FRAIS_NON_REGLES"));
        }
        Integer idEntreprise = entrepriseRepository.findByIdCandidat(idCandidat).map(Entreprise::getIdEntreprise).orElse(null);
        retraitRepository.save(new RetraitDao(null, idDmc, idCandidat, idEntreprise, d.getNomFichier(),
                l.fiche().getNumeroVersion(), LocalDateTime.now(), recu == null ? null : recu.getIdRecu()));
        return d;
    }

    // ------------------------------------------------------------------ PRMP

    /** Le registre des retraits, du plus ancien au plus récent : PRMP de la fiche seule (403), 404 DMC inconnu. */
    @Transactional(readOnly = true)
    public List<ProcedureEnLigneDto.Retrait> retraits(Long idDmc) {
        if (CurrentUser.profil().orElse(null) != ProfilUtilisateur.PRMP) {
            throw new AccessDeniedException("Le registre des retraits du DAO se lit par la PRMP de la fiche.");
        }
        fiches.controlerLecture(idDmc);
        List<ProcedureEnLigneDto.Retrait> out = new ArrayList<>();
        for (RetraitDao r : retraitRepository.findByIdDmcOrderByDateRetraitAscIdRetraitAsc(idDmc)) {
            CompteCandidat c = compteRepository.findById(r.getIdCandidat()).orElse(null);
            Entreprise e = r.getIdEntreprise() == null ? null : entrepriseRepository.findById(r.getIdEntreprise()).orElse(null);
            out.add(new ProcedureEnLigneDto.Retrait(r.getDateRetrait(), c == null ? r.getIdCandidat() : c.getEmail(),
                    e == null ? null : e.getRaisonSociale(), e == null ? null : e.getNif(), r.getCodeDocument(),
                    r.getVersionFiche(), r.getIdRecu() == null ? null : recuRepository.findById(r.getIdRecu())
                            .map(x -> new ProcedureEnLigneDto.RecuRetrait(x.getEtat(), x.getReferencePaiement())).orElse(null)));
        }
        return out;
    }

    // ------------------------------------------------------------------ lecture

    Lue exiger(Long idDmc) {
        return lire(idDmc, LocalDateTime.now())
                .orElseThrow(() -> new ResourceNotFoundException("Procédure en ligne introuvable : " + idDmc + "."));
    }

    /** La procédure si elle remplit les critères (close comprise), vide sinon. */
    private Optional<Lue> lire(Long idDmc, LocalDateTime maintenant) {
        FicheMarcheService.EtatVersion etat;
        try {
            etat = fiches.etatValide(idDmc).orElse(null);
        } catch (ResourceNotFoundException | cnm.prs.exception.BusinessRuleException e) {
            return Optional.empty();   // DMC inconnu ou qui n'est pas un DAO
        }
        if (etat == null || !RemiseElectronique.electronique(etat.etat().getCadrage())) {
            return Optional.empty();
        }
        if (RemiseElectronique.rangNiveau(etat.valeur(CHAMP_SIGNATURE)) > 0) {
            return Optional.empty();   // Q5 : Avancée ou Qualifiée, pas encore
        }
        List<FicheMarche> versions = ficheRepository.findByIdDmcOrderByNumeroVersionAsc(idDmc);
        List<DocumentFicheDto> avis = documents.listerAvis(versions).stream()
                .filter(d -> DocumentsFicheMarcheService.TYPE_AVIS.equals(d.type())).toList();
        if (avis.isEmpty()) {
            return Optional.empty();   // pas lancée
        }
        FicheMarche fiche = versions.stream().filter(v -> v.getNumeroVersion().equals(etat.version())).findFirst().orElse(null);
        if (fiche == null) {
            return Optional.empty();
        }
        return Optional.of(new Lue(construire(idDmc, etat, avis, maintenant), fiche, etat));
    }

    /**
     * ⚠️ V67 (2026-10-04, lot 2a, §B2) — la <strong>vue d'un membre de CAO</strong> : la même forme que la liste publique, lue
     * sur la version courante de la fiche (validée ou non), <strong>sans les critères</strong> de la liste — il voit sa
     * procédure avant son lancement. {@code datePublication} et {@code etat} sont {@code null} tant que rien ne les fonde.
     * 404 DMC inconnu. Sans contrôle de périmètre : l'appelant a vérifié qu'il siège.
     */
    @Transactional(readOnly = true)
    public ProcedureEnLigneDto vue(Long idDmc) {
        FicheMarcheService.EtatVersion etat = fiches.etatCourant(idDmc)
                .orElseThrow(() -> new ResourceNotFoundException("La fiche de la procédure " + idDmc + " n'est pas encore enregistrée."));
        List<DocumentFicheDto> avis = documents.listerAvis(ficheRepository.findByIdDmcOrderByNumeroVersionAsc(idDmc)).stream()
                .filter(d -> DocumentsFicheMarcheService.TYPE_AVIS.equals(d.type())).toList();
        return construire(idDmc, etat, avis, LocalDateTime.now());
    }

    private ProcedureEnLigneDto construire(Long idDmc, FicheMarcheService.EtatVersion etat, List<DocumentFicheDto> avis,
            LocalDateTime maintenant) {
        LocalDateTime limite = dateLimite(etat);
        LocalDateTime ouvertureDepots = RemiseElectronique.dateHeureLue(brute(etat, RemiseElectronique.OUVERTURE_DEPOTS));
        String etatProcedure = limite == null && ouvertureDepots == null ? null
                : limite != null && !maintenant.isBefore(limite) ? CLOSE
                : ouvertureDepots != null && maintenant.isBefore(ouvertureDepots) ? A_VENIR : OUVERTE;
        String numeroDao = brute(etat, DocumentsFicheMarcheService.CHAMP_NUMERO_DAO);
        List<ProcedureEnLigneDto.Lot> lots = lots(etat);
        List<ProcedureEnLigneDto.Frais> frais = frais(etat, lots);
        boolean payant = frais != null && !avis.isEmpty() && apresBascule(avis.get(avis.size() - 1).dateGeneration());
        ParametreService.CompteDao compte = frais == null ? null : parametres.compteDao();
        return new ProcedureEnLigneDto(idDmc,
                numeroDao != null ? numeroDao : etat.etat().getRefeDossier(),
                etat.etat().getDesignationMarche(),
                etat.valeur(CHAMP_AUTORITE),
                etat.categorie(),
                lots,
                avis.isEmpty() ? RemiseElectronique.isoMinute(RemiseElectronique.dateHeureLue(brute(etat, RemiseElectronique.PUBLICATION_AVIS)))
                        : datePublication(avis, etat),
                RemiseElectronique.isoMinute(ouvertureDepots),
                RemiseElectronique.isoMinute(limite),
                etat.valeur(CHAMP_HEURE_REFERENCE),
                Optional.ofNullable(etat.valeur(CHAMP_SIGNATURE)).orElse(RemiseElectronique.NIVEAUX.get(0)),
                ChampFicheMarche.liste(brute(etat, CHAMP_FORMATS)),
                entier(brute(etat, CHAMP_TAILLE_FICHIER)),
                entier(brute(etat, CHAMP_TAILLE_OFFRE)),
                etat.valeur(CHAMP_ASSISTANCE),
                etatProcedure,
                remplacementAutorise(etat),
                OUVERTE.equals(etatProcedure),
                frais,
                compte == null || compte.numeroCompte() == null ? null
                        : new ProcedureEnLigneDto.CompteDao(compte.banque(), compte.titulaire(), compte.numeroCompte()),
                payant);
    }


    // ------------------------------------------------------------------ ⚠️ 2026-10-06 — le retrait après paiement des frais

    /** Les frais de dossier : {@code B04-DS-05}, contrat-cadre de travaux {@code B04-DK-04} ; le dossier commun aux lots. */
    static final List<String> CHAMPS_FRAIS = List.of("B04-DS-05", "B04-DK-04");
    /** H2 — le retrait est payant pour une procédure dont l'avis est imprimé pour la première fois à partir de ce moment (V72). */
    static final String RETRAIT_PAYANT_DEPUIS = "RETRAIT_PAYANT_DEPUIS";

    /** Les frais par lot ({@code CODE#n}, à défaut {@code CODE}) ; {@code null} si aucun lot n'a de frais (dossier gratuit). */
    static List<ProcedureEnLigneDto.Frais> frais(FicheMarcheService.EtatVersion etat, List<ProcedureEnLigneDto.Lot> lots) {
        Map<String, String> v = etat.etat().getValeurs() == null ? Map.of() : etat.etat().getValeurs();
        List<Integer> numeros = new ArrayList<>();
        if (lots.size() > 1) {
            lots.forEach(l -> numeros.add(l.numero()));
        } else {
            numeros.add(null);
        }
        List<ProcedureEnLigneDto.Frais> out = new ArrayList<>();
        boolean payant = false;
        for (Integer n : numeros) {
            java.math.BigDecimal montant = null;
            for (String code : CHAMPS_FRAIS) {
                String x = n == null ? null : v.get(code + LotsFiche.SEPARATEUR + n);
                if (x == null || x.isBlank()) {
                    x = v.get(code);
                }
                montant = x == null || x.isBlank() ? null : SeanceService.montant(x);
                if (montant != null) {
                    break;
                }
            }
            payant |= montant != null && montant.signum() > 0;
            out.add(new ProcedureEnLigneDto.Frais(n, montant));
        }
        return payant ? out : null;
    }

    private boolean apresBascule(LocalDateTime premierAvis) {
        String b = parametres.texte(RETRAIT_PAYANT_DEPUIS);
        LocalDateTime bascule;
        try {
            bascule = b == null ? null : LocalDateTime.parse(b);
        } catch (java.time.format.DateTimeParseException e) {
            bascule = RemiseElectronique.dateHeureLue(b);
        }
        return bascule == null || premierAvis == null || !premierAvis.isBefore(bascule);
    }

    /** Le reçu validé de l'entreprise (son NIF) qui couvre le lot ({@code lot = null} : n'importe lequel), le plus ancien d'abord. */
    @Transactional(readOnly = true)
    public Optional<RecuDao> recuValide(Long idDmc, String nif, Integer lot) {
        if (nif == null) {
            return Optional.empty();
        }
        return recuRepository.findByIdDmcAndNifOrderByDateDepotDescIdRecuDesc(idDmc, nif).stream()
                .filter(r -> RecuDao.VALIDE.equals(r.getEtat()))
                .filter(r -> lot == null || r.getLots() == null || ChampFicheMarche.liste(r.getLots()).contains(String.valueOf(lot)))
                .min(Comparator.comparing(RecuDao::getDateDecision, Comparator.nullsLast(Comparator.naturalOrder())));
    }

    /** Le NIF de l'entreprise déclarée par un compte candidat ; {@code null} sans entreprise. */
    @Transactional(readOnly = true)
    public String nifDe(String idCandidat) {
        return idCandidat == null ? null : entrepriseRepository.findByIdCandidat(idCandidat).map(Entreprise::getNif).orElse(null);
    }
    /** ⚠️ 2026-10-04 (lot 3, §B1) — {@code B04-SE-10 = OUI} : remplacer et retirer son offre avant la date limite. */
    static boolean remplacementAutorise(FicheMarcheService.EtatVersion etat) {
        return "OUI".equalsIgnoreCase(brute(etat, CHAMP_REMPLACEMENT));
    }

    /** ⚠️ 2026-10-04 (lot 3, §B3) — {@code B04-SE-09} en octets ({@code null} si non renseigné). */
    static Long tailleMaxOffreOctets(FicheMarcheService.EtatVersion etat) {
        Integer mo = entier(brute(etat, CHAMP_TAILLE_OFFRE));
        return mo == null ? null : mo * 1024L * 1024L;
    }

    /** La procédure si elle remplit les critères de la liste publique (close comprise), vide sinon. */
    Optional<Lue> trouver(Long idDmc) {
        return lire(idDmc, LocalDateTime.now());
    }

    /**
     * ⚠️ 2026-10-04 (lot 3, §B2) — les <strong>pièces attendues</strong> de l'offre, publiques : 404 hors des critères de la liste.
     * En tête de la rubrique {@code OFFRE} : l'acte d'engagement signé ({@code AE}, par lot si alloti), le reçu des frais de dossier
     * ({@code RECU-DAO}, Q3 du plan, à confirmer par le juriste), la garantie de soumission si le cadrage l'exige ({@code GARANTIE},
     * voie B) ; puis les pièces exigées de la fiche (bloc B14, dernière version validée), {@code OFFRE} puis {@code ADMINISTRATIVE},
     * dans l'ordre du DAO. {@code code} : {@code PIECE-<idPiece>}, stable pour une version de la fiche.
     */
    @Transactional(readOnly = true)
    public List<cnm.prs.dto.OffreDto.PieceAttendue> piecesAttendues(Long idDmc) {
        Lue l = exiger(idDmc);
        boolean alloti = l.dto().lots().size() > 1;
        List<cnm.prs.dto.OffreDto.PieceAttendue> out = new ArrayList<>();
        out.add(new cnm.prs.dto.OffreDto.PieceAttendue("AE", PiecesFiche.OFFRE, null, "Acte d'engagement signé", "Original signé",
                null, alloti, null, true, null, null));
        // ⚠️ 2026-10-06 (§B5) — un dossier payant : le reçu validé est la preuve du paiement, il n'est plus redemandé dans l'offre.
        // Sans frais renseignés, la pièce reste exigée comme avant. Relatif au candidat connecté (sans session : générique).
        boolean payant = l.dto().retraitPayant();
        Boolean dejaFourni = payant ? recuValide(idDmc, CurrentUser.ref().filter(x -> cnm.prs.enums.TypeActeur.CANDIDAT.name()
                .equals(CurrentUser.acteurType().orElse(null))).map(this::nifDe).orElse(null), null).isPresent() : null;
        out.add(new cnm.prs.dto.OffreDto.PieceAttendue("RECU-DAO", PiecesFiche.OFFRE, null, "Reçu du paiement des frais de dossier",
                "Copie", null, false, null, !payant, null, dejaFourni));
        Map<String, Object> cadrage = l.etat().etat().getCadrage();
        if (cadrage != null && "OUI".equalsIgnoreCase(String.valueOf(cadrage.get("garantieSoumission")))) {
            out.add(new cnm.prs.dto.OffreDto.PieceAttendue("GARANTIE", PiecesFiche.OFFRE, null,
                    "Garantie de soumission (document et code de vérification)", "Original", null, alloti, null, true, null, null));
        }
        // ⚠️ 2026-10-05 (lot 5, §B1.3) — avec un besoin, les pièces que remplit un formulaire ne sont plus exigées en fichier.
        String categorie = l.etat().categorie();
        boolean travaux = cnm.prs.enums.CategorieDao.TRAVAUX.name().equals(categorie);
        boolean formulaires = !cnm.prs.enums.CategorieDao.PRESTATIONS_INTELLECTUELLES.name().equals(categorie)
                && !besoin.lister(l.fiche().getIdFiche()).isEmpty();
        List<cnm.prs.dto.PieceExigeeDto> exigees = piecesFiche.pieces(l.fiche().getIdFiche());
        for (String rubrique : List.of(PiecesFiche.OFFRE, PiecesFiche.ADMINISTRATIVE)) {
            for (cnm.prs.dto.PieceExigeeDto p : exigees) {
                if (rubrique.equals(p.getRubrique())) {
                    out.add(new cnm.prs.dto.OffreDto.PieceAttendue("PIECE-" + p.getIdPiece(), p.getRubrique(), p.getNumero(),
                            p.getLibelle(), p.getForme(), p.getAncienneteMaxMois(), Boolean.TRUE.equals(p.getParLot()) && alloti,
                            p.getModele(), formulaire(p.getLibelle(), travaux, formulaires) == null,
                            formulaire(p.getLibelle(), travaux, formulaires), null));
                }
            }
        }
        return out;
    }

    private static String formulaire(String libelle, boolean travaux, boolean formulaires) {
        return formulaires ? FormulairesEnLigne.formulaire(libelle, travaux) : null;
    }

    /**
     * La date limite de remise : {@code B04-LR-03} + {@code B04-LR-04} (fournitures, l'heure manquante vaut 00:00) ; à défaut
     * {@code B04-CP-02} (contrat-cadre) ; à défaut {@code B04-OV-02} (travaux) — même ordre que les formulaires du candidat.
     */
    static LocalDateTime dateLimite(FicheMarcheService.EtatVersion etat) {
        String jour = brute(etat, FormulairesCandidat.REMISE_OFFRES);
        if (jour != null) {
            LocalDateTime avecHeure = RemiseElectronique.echeance(jour, brute(etat, "B04-LR-04"));
            return avecHeure != null ? avecHeure : RemiseElectronique.dateHeureLue(jour);
        }
        LocalDateTime cc = RemiseElectronique.dateHeureLue(brute(etat, FormulairesCandidat.REMISE_OFFRES_CONTRAT_CADRE));
        return cc != null ? cc : RemiseElectronique.dateHeureLue(brute(etat, FormulairesCandidat.REMISE_OFFRES_TRAVAUX));
    }

    /** La date de la première publication saisie à l'impression de l'avis ; à défaut {@code B04-SE-17}. */
    private static String datePublication(List<DocumentFicheDto> avis, FicheMarcheService.EtatVersion etat) {
        DocumentFicheDto premier = avis.get(avis.size() - 1);   // listerAvis : du plus récent au plus ancien
        Object saisie = premier.publication() == null ? null : premier.publication().get("datePublication");
        if (saisie != null && !String.valueOf(saisie).isBlank()) {
            return String.valueOf(saisie);
        }
        return RemiseElectronique.isoMinute(RemiseElectronique.dateHeureLue(brute(etat, RemiseElectronique.PUBLICATION_AVIS)));
    }

    /** Les lots du plan (ligne courante), numérotés dans l'ordre ; vide pour un marché non alloti. */
    private List<ProcedureEnLigneDto.Lot> lots(FicheMarcheService.EtatVersion etat) {
        Integer idDetail = etat.etat().getIdDetailCourant() != null ? etat.etat().getIdDetailCourant() : etat.etat().getIdDetail();
        if (idDetail == null) {
            return List.of();
        }
        List<Lot> lots = new ArrayList<>(lotRepository.findByIdDetail(idDetail));
        lots.sort(Comparator.comparing(Lot::getIdLot));
        List<ProcedureEnLigneDto.Lot> out = new ArrayList<>();
        for (int i = 0; i < lots.size(); i++) {
            Lot l = lots.get(i);
            out.add(new ProcedureEnLigneDto.Lot(i + 1, l.getDesignationLot() == null ? "Lot " + (i + 1) : l.getDesignationLot()));
        }
        return out;
    }

    /** La valeur enregistrée d'un champ, si la version l'imprime ({@link FicheMarcheService.EtatVersion#valeur}) ; sinon {@code null}. */
    private static String brute(FicheMarcheService.EtatVersion etat, String code) {
        if (etat.valeur(code) == null) {
            return null;
        }
        Map<String, String> v = etat.etat().getValeurs();
        String s = v == null ? null : v.get(code);
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static Integer entier(String v) {
        if (v == null) {
            return null;
        }
        try {
            return new java.math.BigDecimal(v.replace(',', '.').replace(" ", "")).intValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
