package cnm.prs.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cnm.prs.entity.ChampFicheMarche;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.DocumentFicheDto;
import cnm.prs.dto.FicheMarcheDto;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.DocumentFicheMarche;
import cnm.prs.entity.FicheMarche;
import cnm.prs.entity.PieceJointeDossier;
import cnm.prs.entity.TypePieceJointe;
import cnm.prs.enums.StatutDossier;
import cnm.prs.enums.StatutFicheMarche;
import cnm.prs.exception.GenerationDocumentsException;
import cnm.prs.repository.BlocFicheMarcheRepository;
import cnm.prs.repository.ChampFicheMarcheRepository;
import cnm.prs.repository.DocumentFicheMarcheRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.FicheMarcheRepository;
import cnm.prs.repository.PieceJointeDossierRepository;
import cnm.prs.repository.RubriqueFicheMarcheRepository;
import cnm.prs.repository.TypePieceJointeRepository;

/**
 * ⚠️ <strong>Les documents générés depuis la fiche marché</strong> (demande front du 2026-09-23, lot 2a).
 *
 * <ul>
 *   <li><strong>Production</strong> ({@link #produire}) : appelée dans la transaction de la validation, <em>avant</em> que
 *       la version ne soit figée — un échec lève {@link GenerationDocumentsException} et la version reste en brouillon.
 *       Contenu choisi par {@link SelectionDocumentsFiche}, disposé par {@link GenerateurDocumentsFiche} ; docx et pdf
 *       par document, rattachés à l'{@code idFiche}.</li>
 *   <li><strong>Jointure au dossier</strong> ({@link #joindre}) : le dossier soumis que porte la fiche reçoit les
 *       <strong>PDF</strong> de la dernière version validée comme pièces du type de code {@code DAO_COMPLET} (« Dossier
 *       d'appel d'offres complet »), marquées {@code idDocumentFiche}. Les pièces d'une version précédente sont
 *       détachées (supprimées du dossier ; le document reste lisible par sa version).</li>
 * </ul>
 */
@Service
@Transactional
public class DocumentsFicheMarcheService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DocumentsFicheMarcheService.class);

    /** ⚠️ V59 — la catégorie dont le besoin est un DQE (bordereau des prix et DQE, ni LF ni TC). */
    private static final String TRAVAUX = cnm.prs.enums.CategorieDao.TRAVAUX.name();
    /** ⚠️ 2026-10-03 — le numéro du dossier d'appel d'offres, imprimé en tête des classeurs. */
    static final String CHAMP_NUMERO_DAO = "B02-OB-03";

    /** Code stable du type de pièce « Dossier d'appel d'offres complet » (V38). */
    public static final String CODE_TYPE_PIECE = "DAO_COMPLET";
    /**
     * ⚠️ 2026-09-30 — le type des documents de l'avis spécifique d'appel d'offres : imprimés à la demande après le PV,
     * rattachés à la version validée qu'ils rendent, jamais joints au dossier comme pièce du DAO.
     */
    public static final String TYPE_AVIS = "AVIS";
    /**
     * ⚠️ 2026-10-01 (lot AV-4.1) — le type des lettres d'invitation des prestations intellectuelles : une paire par candidat
     * de la liste restreinte, imprimée à la demande comme l'avis, jamais jointe au dossier.
     */
    public static final String TYPE_LETTRE = "LETTRE_INVITATION";
    /** Le début du nom de fichier d'une lettre d'invitation ({@code LETTRE_<plan>_<ligne>_v2_<horodatage>_01.pdf}). */
    static final String PREFIXE_FICHIER_LETTRE = "LETTRE";
    /** ⚠️ 2026-10-01 — les publications : imprimées à la demande, listées à part, jamais pièces du DAO, uniques non. */
    public static final Set<String> TYPES_PUBLICATION = Set.of(TYPE_AVIS, TYPE_LETTRE);

    /**
     * ⚠️ R3b (manuel révisé pour l'application, lot 1, §B3 ; 2026-10-13) — le <strong>projet</strong> d'avis spécifique
     * ({@code AVIS_PROJET}, fournitures et travaux) ou de lettre d'invitation ({@code LETTRE_PROJET}, prestations intellectuelles),
     * produit à la validation de chaque version (sans publication ni destinataire : pointillés ; « PROJET » en filigrane) et joint
     * d'office au dossier DMC sous les pièces {@code AVIS_SPECIFIQUE} / {@code LETTRE_INVITATION}. Ni une publication (la ligne ne
     * passe pas « Lancé »), ni un document du DAO (hors de la liste de la version et du DAO complet).
     */
    public static final String TYPE_AVIS_PROJET = "AVIS_PROJET";
    public static final String TYPE_LETTRE_PROJET = "LETTRE_PROJET";
    public static final Set<String> TYPES_PROJET = Set.of(TYPE_AVIS_PROJET, TYPE_LETTRE_PROJET);
    static final String FILIGRANE_PROJET = "PROJET";

    /**
     * ⚠️ V117 (2026-10-14, demande front « q11-prix-estimatifs », §B3) — les documents <strong>estimatifs</strong> d'une version : le
     * bordereau des prix estimatifs (fournitures) ou le devis quantitatif et estimatif (travaux), un PDF par lot, produits à la
     * validation depuis les prix unitaires estimés (V116) et joints d'office au dossier sous les pièces {@code BORDEREAU_PRIX} /
     * {@code DQE}. Jamais publiés : hors de la liste du DAO, du DAO complet, du retrait par les candidats et du cahier des charges.
     */
    public static final String TYPE_BORDEREAU_ESTIMATIF = "BORDEREAU_ESTIMATIF";
    public static final String TYPE_DQE_ESTIMATIF = "DQE_ESTIMATIF";
    public static final Set<String> TYPES_ESTIMATIF = Set.of(TYPE_BORDEREAU_ESTIMATIF, TYPE_DQE_ESTIMATIF);
    static final String CODE_PIECE_BORDEREAU = "BORDEREAU_PRIX";
    static final String CODE_PIECE_DQE = "DQE";

    /**
     * ⚠️ V117 — les documents d'une version qui <strong>ne sont pas du DAO</strong> remis aux candidats : publications, projets de
     * publication, estimatifs.
     */
    public static boolean horsDao(String type) {
        return TYPES_PUBLICATION.contains(type) || TYPES_PROJET.contains(type) || TYPES_ESTIMATIF.contains(type);
    }
    /** Les codes des types de pièce qui reçoivent le projet. */
    static final String CODE_PIECE_AVIS = "AVIS_SPECIFIQUE";
    static final String CODE_PIECE_LETTRE = "LETTRE_INVITATION";
    /**
     * ⚠️ 2026-10-01 — le bloc de signature de l'avis et de la lettre : leurs trois derniers paragraphes (lieu et date ou
     * formule de politesse, qualité, nom).
     */
    static final int BLOC_SIGNATURE_AVIS = 3;
    private static final java.time.format.DateTimeFormatter HORODATAGE =
            java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** Statuts où le dossier reçoit les documents d'une nouvelle version à la place des précédents (ceux du dépôt). */
    private static final Set<String> STATUTS_REMPLACEMENT = Set.of(StatutDossier.BROUILLON.name(),
            StatutDossier.SOUMIS.name(), StatutDossier.EN_ATTENTE_COMPLEMENTS_DEPOT.name());

    /**
     * ⚠️ Lot C (2026-09-27, §B2) — statuts où le dossier est <strong>rendu à la PRMP</strong> après examen : les documents
     * de la révision s'ajoutent en <em>version corrigée</em>, la version précédente de chaque pièce conservée (comme les
     * pièces corrigées d'un plan). En attente de pièces, ils sont en outre rattachés à la dernière lettre de renvoi : ce
     * sont les compléments du dossier DAO.
     */
    private static final Set<String> STATUTS_RECTIFICATION = Set.of(StatutDossier.EN_ATTENTE_DECISION_PRMP.name(),
            StatutDossier.EN_ATTENTE_PIECES.name());

    private final DocumentFicheMarcheRepository documentRepository;
    private final GenerateurDocumentsFiche generateur;
    private final ChampFicheMarcheRepository champRepository;
    private final BlocFicheMarcheRepository blocRepository;
    private final RubriqueFicheMarcheRepository rubriqueRepository;
    private final FicheMarcheRepository ficheRepository;
    private final DossierRepository dossierRepository;
    private final PieceJointeDossierRepository pieceRepository;
    private final TypePieceJointeRepository typePieceRepository;
    /** ⚠️ V45 (2026-09-25) — classeurs du candidat et taux de TVA administrable. */
    private final GenerateurClasseursFiche classeurs;
    private final ParametreService parametres;
    /** ⚠️ V47 (2026-09-26) — les modèles officiels des formulaires du candidat, lus au démarrage. */
    private final ModelesCandidat modelesCandidat;
    /** ⚠️ Lot D (2026-09-28) — les documents types officiels du DAO (contrat-cadre : DPAC, AE), lus au démarrage. */
    private final ModelesDao modelesDao;
    /** ⚠️ R3c — le mode du plan : en appel d'offres restreint, la lettre AOR remplace l'avis (publication et projet). */
    private final ValeursPpmService valeursPpm;
    /** ⚠️ Lot C (2026-09-27) — la lettre de renvoi à laquelle rattacher les documents d'une révision (compléments). */
    private final cnm.prs.repository.LettreRenvoiRepository lettreRenvoiRepository;

    public DocumentsFicheMarcheService(DocumentFicheMarcheRepository documentRepository,
            GenerateurDocumentsFiche generateur, ChampFicheMarcheRepository champRepository,
            BlocFicheMarcheRepository blocRepository, RubriqueFicheMarcheRepository rubriqueRepository,
            FicheMarcheRepository ficheRepository, DossierRepository dossierRepository,
            PieceJointeDossierRepository pieceRepository, TypePieceJointeRepository typePieceRepository,
            GenerateurClasseursFiche classeurs, ParametreService parametres, ModelesCandidat modelesCandidat,
            cnm.prs.repository.LettreRenvoiRepository lettreRenvoiRepository, ModelesDao modelesDao, ValeursPpmService valeursPpm) {
        this.valeursPpm = valeursPpm;
        this.modelesDao = modelesDao;
        this.lettreRenvoiRepository = lettreRenvoiRepository;
        this.modelesCandidat = modelesCandidat;
        this.classeurs = classeurs;
        this.parametres = parametres;
        this.documentRepository = documentRepository;
        this.generateur = generateur;
        this.champRepository = champRepository;
        this.blocRepository = blocRepository;
        this.rubriqueRepository = rubriqueRepository;
        this.ficheRepository = ficheRepository;
        this.dossierRepository = dossierRepository;
        this.pieceRepository = pieceRepository;
        this.typePieceRepository = typePieceRepository;
    }

    /** Un document prêt à enregistrer, produit avant que la version ne soit figée. */
    public record Produit(String type, String extension, String nomFichier, byte[] contenu, Integer lot) {
    }

    /**
     * Produit les fichiers de la version décrite par {@code etat} (son état figé, tel que la validation l'a contrôlé).
     * N'écrit rien : {@link #enregistrer} les rattache une fois la version validée.
     *
     * @throws GenerationDocumentsException si un document ne se produit pas
     */
    public List<Produit> produire(FicheMarcheDto etat, LocalDateTime validation) {
        return produire(etat, List.of(), validation);
    }

    /**
     * ⚠️ V45 (2026-09-25, formulaires du candidat, §B3) — avec le besoin d'une fiche de fournitures ({@code articles},
     * vide sinon) : la liste des fournitures et calendrier ({@code LF}, docx et pdf) et, par lot, le bordereau des prix
     * ({@code BP}) et le tableau de conformité ({@code TC}) en classeurs {@code xlsx}.
     */
    public List<Produit> produire(FicheMarcheDto etat, List<BesoinFiche.Article> articles, LocalDateTime validation) {
        return produire(etat, articles, validation, Map.of());
    }

    /**
     * ⚠️ V60 (2026-10-03, §B2) — et des jetons calculés par l'appelant hors de la fiche : {@code {{MOYENS.materiel}}},
     * {@code {{MOYENS.personnel}}} (listes de la version, {@link MoyensFiche#jetons}).
     */
    public List<Produit> produire(FicheMarcheDto etat, List<BesoinFiche.Article> articles, LocalDateTime validation,
            Map<String, String> jetons) {
        List<ChampFicheMarche> champs = champRepository.findByActifTrueOrderByCodeRubriqueAscRangAsc();
        List<DocumentFicheModele> modeles = new ArrayList<>(SelectionDocumentsFiche.selectionner(etat, champs,
                blocRepository.findAllByOrderByRangAsc(), rubriqueRepository.findAllByOrderByCodeBlocAscRangAscCodeAsc(), validation));
        Map<String, ChampFicheMarche> parCode = new LinkedHashMap<>();
        champs.forEach(c -> parCode.put(c.getCode(), c));
        List<Produit> produits = new ArrayList<>();
        // ⚠️ Lot D (2026-09-28, §B3) — les documents décrits sur le document type officiel (contrat-cadre, fournitures et
        // services : DPAC, AE par lot) sont rendus depuis leur fichier de commande, À LA PLACE de la liste « libellé :
        // valeur » du lot 2a pour ces types ; les autres formes gardent le lot 2a (repli par type).
        List<ModelesDao.Couverture> couvertes = ModelesDao.couvertures(etat.getTypeMarche(), etat.getCategorie());
        java.util.Set<String> remplaces = new java.util.HashSet<>();
        Map<String, String> parametresDocuments = parametresDocuments();
        int nbLots = Boolean.TRUE.equals(etat.getSaisieParLot()) && etat.getNbLots() != null ? etat.getNbLots() : 0;
        // ⚠️ V59 (2026-10-02, DQE des travaux, §B1.5) — les séries du DQE, jeton {{BESOIN.series}} (découpage du forfait).
        parametresDocuments.putAll(FormulairesCandidat.seriesDuBesoin(articles, nbLots));
        if (jetons != null) {
            parametresDocuments.putAll(jetons);
        }
        for (ModelesDao.Couverture c : couvertes) {
            remplaces.add(c.typeDocument());
            List<Integer> lots = new ArrayList<>();
            if (SelectionDocumentsFiche.parLot(c.typeDocument()) && LotsFiche.alloti(nbLots)) {
                for (int n = 1; n <= nbLots; n++) {
                    lots.add(n);
                }
            } else {
                lots.add(null);
            }
            for (Integer lot : lots) {
                DocumentLibre doc;
                List<GenerateurDocumentsFiche.Fichier> fichiers;
                try {
                    doc = FormulairesCandidat.rendreModele(c.typeDocument(), lot, etat, parCode, modelesDao.modele(c.sigle()), validation,
                            parametresDocuments);
                    fichiers = generateur.generer(doc);
                } catch (RuntimeException e) {
                    throw new GenerationDocumentsException("La génération du document « "
                            + SelectionDocumentsFiche.titre(c.typeDocument(), lot, etat.getTypeMarche(), etat.getCategorie()) + " » a échoué : la "
                            + "version n'est pas validée. " + e.getMessage(), e);
                }
                for (GenerateurDocumentsFiche.Fichier f : fichiers) {
                    produits.add(new Produit(doc.type(), f.extension(), nomFichier(doc.type(), etat.getRefeDossier(),
                            etat.getIdDetail(), lot, etat.getVersion(), f.extension()), f.contenu(), lot));
                }
            }
        }
        modeles.removeIf(m -> remplaces.contains(m.type()));
        // ⚠️ V59 — pas de liste des fournitures aux travaux : leur besoin est le DQE, chiffré dans le classeur BP.
        DocumentFicheModele liste = TRAVAUX.equals(etat.getCategorie()) ? null
                : SelectionDocumentsFiche.listeFournitures(etat, articles, validation);
        if (liste != null) {
            modeles.add(liste);
        }
        for (DocumentFicheModele modele : modeles) {
            List<GenerateurDocumentsFiche.Fichier> fichiers;
            try {
                fichiers = generateur.generer(modele);
            } catch (RuntimeException e) {
                throw new GenerationDocumentsException("La génération du document « " + modele.titre() + " » ("
                        + modele.type() + ") a échoué : la version n'est pas validée. " + e.getMessage(), e);
            }
            for (GenerateurDocumentsFiche.Fichier f : fichiers) {
                produits.add(new Produit(modele.type(), f.extension(), nomFichier(modele.type(), etat.getRefeDossier(),
                        etat.getIdDetail(), modele.lot(), etat.getVersion(), f.extension()), f.contenu(), modele.lot()));
            }
        }
        // ⚠️ V47 (2026-09-26, §B8, R12 (c)) — fiches A1 à A4 (une fois pour le dossier) et garanties C1/C2 (par lot), sur
        // les modèles officiels décalqués, toutes catégories.
        for (DocumentLibre modele : FormulairesCandidat.generer(etat, parCode, modelesCandidat.modeles(), validation)) {
            List<GenerateurDocumentsFiche.Fichier> fichiers;
            try {
                fichiers = generateur.generer(modele);
            } catch (RuntimeException e) {
                throw new GenerationDocumentsException("La génération du document « "
                        + SelectionDocumentsFiche.titre(modele.type(), modele.lot()) + " » a échoué : la version n'est pas "
                        + "validée. " + e.getMessage(), e);
            }
            // ⚠️ 2026-10-02 — une garantie de soumission des travaux se nomme B1 / B2 (dossier type des travaux).
            String prefixe = SelectionDocumentsFiche.prefixeFichier(modele.type(), etat.getCategorie());
            for (GenerateurDocumentsFiche.Fichier f : fichiers) {
                produits.add(new Produit(modele.type(), f.extension(), nomFichier(prefixe, etat.getRefeDossier(),
                        etat.getIdDetail(), modele.lot(), etat.getVersion(), f.extension()), f.contenu(), modele.lot()));
            }
        }
        produits.addAll(classeurs(etat, articles));
        return produits;
    }

    /** ⚠️ V45 — bordereau des prix et tableau de conformité de chaque lot qui a des articles. */
    private List<Produit> classeurs(FicheMarcheDto etat, List<BesoinFiche.Article> articles) {
        List<Produit> produits = new ArrayList<>();
        if (articles == null || articles.isEmpty()) {
            return produits;
        }
        boolean aCommande = "A_COMMANDE".equals(etat.getTypeMarche());
        int nbLots = Boolean.TRUE.equals(etat.getSaisieParLot()) && etat.getNbLots() != null ? etat.getNbLots() : 0;
        java.math.BigDecimal tva = parametres.tauxTva();
        List<Integer> lots = new ArrayList<>();
        if (LotsFiche.alloti(nbLots)) {
            for (int n = 1; n <= nbLots; n++) {
                lots.add(n);
            }
        } else {
            lots.add(null);
        }
        // ⚠️ V59 (2026-10-02, §B1.3) — travaux : le bordereau des prix et DQE seul (pas de tableau de conformité).
        boolean travaux = TRAVAUX.equals(etat.getCategorie());
        Object typePrix = etat.getCadrage() == null ? null : etat.getCadrage().get("typePrix");
        // ⚠️ 2026-10-03 — l'en-tête dit le numéro du DAO (B02-OB-03), pas seulement la référence du plan de passation.
        String dossier = GenerateurClasseursFiche.ligneDossier(
                etat.getValeurs() == null ? null : etat.getValeurs().get(CHAMP_NUMERO_DAO), etat.getRefeDossier());
        for (String type : travaux ? List.of("BP") : List.of("BP", "TC")) {
            for (Integer lot : lots) {
                List<BesoinFiche.Article> duLot = articles.stream().filter(a -> java.util.Objects.equals(a.lot(), lot)).toList();
                if (duLot.isEmpty()) {
                    continue;
                }
                byte[] contenu;
                try {
                    contenu = travaux
                            ? classeurs.bordereauTravaux(dossier, etat.getDesignationMarche(), lot, duLot,
                                    aCommande, typePrix == null ? null : String.valueOf(typePrix), tva)
                            : "BP".equals(type)
                            ? classeurs.bordereau(dossier, etat.getDesignationMarche(), lot, duLot, aCommande, tva)
                            : classeurs.conformite(dossier, etat.getDesignationMarche(), lot, duLot);
                } catch (RuntimeException e) {
                    throw new GenerationDocumentsException("La génération du document « "
                            + SelectionDocumentsFiche.titre(type, lot, etat.getTypeMarche(), etat.getCategorie())
                            + " » a échoué : la version n'est pas validée. "
                            + e.getMessage(), e);
                }
                produits.add(new Produit(type, "xlsx", nomFichier(type, etat.getRefeDossier(), etat.getIdDetail(), lot,
                        etat.getVersion(), "xlsx"), contenu, lot));
            }
        }
        return produits;
    }

    /**
     * ⚠️ 2026-10-01 (lot AV-4.1 du front, 547e48b) — les jetons {@code {{PARAM.*}}} de tous les documents rendus depuis un
     * modèle : le compte bancaire de l'ARMP ({@code PARAM.compte-dao}), réglé par l'Administrateur. Absent : pointillés.
     * Un document du DAO est figé à la validation de la fiche : il garde le compte réglé à ce moment-là.
     */
    Map<String, String> parametresDocuments() {
        Map<String, String> m = new LinkedHashMap<>();
        String compte = parametres.compteDaoTexte();
        if (compte != null) {
            m.put(FormulairesCandidat.JETON_COMPTE_DAO, compte);
        }
        return m;
    }

    /**
     * ⚠️ Avis spécifique d'appel d'offres (demande front du 2026-09-30, §B1-§B3) — l'avis rendu depuis le modèle de la
     * catégorie ({@link ModelesDao#sigleAvis}), sur l'état figé de la version validée, avec les informations de publication
     * déjà mises en forme ({@code date-publication}, {@code jmp-numero}, {@code jmp-date}, {@code supports}). Un .docx et
     * un .pdf ; le nom porte l'horodatage d'impression, chaque impression produisant une nouvelle paire.
     */
    public List<Produit> produireAvis(FicheMarcheDto etat, Map<String, String> publication, LocalDateTime impression) {
        FichierCommande.Modele modele = modelesDao.modele(ModelesDao.sigleAvis(etat.getCategorie()));
        if (modele == null) {
            throw new GenerationDocumentsException("Aucun modèle d'avis spécifique pour la catégorie " + etat.getCategorie() + ".", null);
        }
        Map<String, ChampFicheMarche> parCode = new LinkedHashMap<>();
        champRepository.findByActifTrueOrderByCodeRubriqueAscRangAsc().forEach(c -> parCode.put(c.getCode(), c));
        DocumentLibre doc;
        List<GenerateurDocumentsFiche.Fichier> fichiers;
        try {
            // ⚠️ 2026-10-01 (contre-recette du front) — le bloc de signature (« à …, le … », la qualité, le nom) gardé ensemble
            Map<String, String> jetons = new LinkedHashMap<>(publication);
            jetons.putAll(parametresDocuments());
            // ⚠️ R3a (manuel révisé, lot 1, §B3) — la décision d'équivalence SIGMP (ou son attente) au pied de chaque page.
            doc = FormulairesCandidat.rendreModele(TYPE_AVIS, null, etat, parCode, modele, impression, jetons)
                    .finGardeeEnsemble(BLOC_SIGNATURE_AVIS).avecPiedDePage(parametres.piedSigmp());
            fichiers = generateur.generer(doc);
        } catch (RuntimeException e) {
            throw new GenerationDocumentsException("La génération de l'avis spécifique a échoué : " + e.getMessage(), e);
        }
        List<Produit> produits = new ArrayList<>();
        for (GenerateurDocumentsFiche.Fichier fi : fichiers) {
            String nom = nomFichier(TYPE_AVIS, etat.getRefeDossier(), etat.getIdDetail(), null, etat.getVersion(), fi.extension());
            nom = nom.substring(0, nom.length() - fi.extension().length() - 1) + "_" + impression.format(HORODATAGE) + "." + fi.extension();
            produits.add(new Produit(TYPE_AVIS, fi.extension(), nom, fi.contenu(), null));
        }
        return produits;
    }

    /**
     * ⚠️ 2026-10-01 (lot AV-4.1, §B3) — les lettres d'invitation : une paire .docx / .pdf par candidat, dans l'ordre saisi
     * (rang 1, 2…), rendues du modèle de la catégorie ({@link ModelesDao#sigleLettre}) sur l'état figé de la version
     * validée. {@code commun} porte les jetons partagés ({@code LETTRE.lieu}, {@code LETTRE.date}, {@code LETTRE.candidats}),
     * {@code destinataires} celui de chaque lettre ({@code LETTRE.destinataire}). Le nom porte l'horodatage et le rang.
     */
    public Map<Integer, List<Produit>> produireLettres(FicheMarcheDto etat, Map<String, String> commun, List<String> destinataires,
            LocalDateTime impression) {
        // ⚠️ R3c — LETTRE-PI, ou LETTRE-AOR pour un appel d'offres restreint de fournitures ou de travaux.
        FichierCommande.Modele modele = modelesDao.modele(ModelesDao.sigleLettre(etat.getCategorie(), valeursPpm.modeRestreint(etat.getIdDetail())));
        if (modele == null) {
            throw new GenerationDocumentsException("Aucun modèle de lettre d'invitation pour la catégorie " + etat.getCategorie() + ".", null);
        }
        Map<String, ChampFicheMarche> parCode = new LinkedHashMap<>();
        champRepository.findByActifTrueOrderByCodeRubriqueAscRangAsc().forEach(c -> parCode.put(c.getCode(), c));
        Map<String, String> parametres = parametresDocuments();
        String piedSigmp = this.parametres.piedSigmp();   // ⚠️ R3a (§B3) — le même pied sur chaque lettre
        Map<Integer, List<Produit>> parRang = new LinkedHashMap<>();
        for (int i = 0; i < destinataires.size(); i++) {
            int rang = i + 1;
            List<GenerateurDocumentsFiche.Fichier> fichiers;
            try {
                Map<String, String> jetons = new LinkedHashMap<>(commun);
                jetons.put(FormulairesCandidat.PREFIXE_LETTRE + "destinataire", destinataires.get(i));
                jetons.putAll(parametres);
                fichiers = generateur.generer(FormulairesCandidat.rendreModele(TYPE_LETTRE, null, etat, parCode, modele, impression,
                        jetons).finGardeeEnsemble(BLOC_SIGNATURE_AVIS).avecPiedDePage(piedSigmp));
            } catch (RuntimeException e) {
                throw new GenerationDocumentsException("La génération de la lettre d'invitation n° " + rang + " a échoué : "
                        + e.getMessage(), e);
            }
            List<Produit> paire = new ArrayList<>();
            for (GenerateurDocumentsFiche.Fichier fi : fichiers) {
                String nom = nomFichier(PREFIXE_FICHIER_LETTRE, etat.getRefeDossier(), etat.getIdDetail(), null, etat.getVersion(),
                        fi.extension());
                nom = nom.substring(0, nom.length() - fi.extension().length() - 1) + "_" + impression.format(HORODATAGE)
                        + String.format("_%02d.", rang) + fi.extension();
                paire.add(new Produit(TYPE_LETTRE, fi.extension(), nom, fi.contenu(), null));
            }
            parRang.put(rang, paire);
        }
        return parRang;
    }

    // ------------------------------------------------------------------ projets de publication (⚠️ R3b, 2026-10-13)

    /**
     * ⚠️ R3b (manuel révisé, lot 1, §B3) — le projet de publication d'une version : l'avis spécifique (fournitures, travaux) ou la
     * lettre d'invitation (prestations intellectuelles), rendu sur l'état de la version sans information de publication ni
     * destinataire (pointillés), le pied d'équivalence SIGMP, « PROJET » en filigrane, sans horodatage d'impression (un par version).
     * Vide si la catégorie n'a pas de modèle. 500 nommé ({@link GenerationDocumentsException}) si le rendu échoue.
     */
    public List<Produit> produireProjet(FicheMarcheDto etat, LocalDateTime validation) {
        // ⚠️ R3c — en appel d'offres restreint, le projet est la lettre AOR (pas d'avis public).
        boolean restreint = valeursPpm.modeRestreint(etat.getIdDetail());
        boolean lettre = ModelesDao.sigleLettre(etat.getCategorie(), restreint) != null;
        String sigle = lettre ? ModelesDao.sigleLettre(etat.getCategorie(), restreint) : ModelesDao.sigleAvis(etat.getCategorie(), restreint);
        FichierCommande.Modele modele = sigle == null ? null : modelesDao.modele(sigle);
        if (modele == null) {
            return List.of();
        }
        String type = lettre ? TYPE_LETTRE_PROJET : TYPE_AVIS_PROJET;
        Map<String, ChampFicheMarche> parCode = new LinkedHashMap<>();
        champRepository.findByActifTrueOrderByCodeRubriqueAscRangAsc().forEach(c -> parCode.put(c.getCode(), c));
        List<GenerateurDocumentsFiche.Fichier> fichiers;
        try {
            fichiers = generateur.generer(FormulairesCandidat.rendreModele(lettre ? TYPE_LETTRE : TYPE_AVIS, null, etat, parCode, modele,
                    validation, parametresDocuments()).finGardeeEnsemble(BLOC_SIGNATURE_AVIS).avecPiedDePage(parametres.piedSigmp())
                    .avecFiligrane(FILIGRANE_PROJET));
        } catch (RuntimeException e) {
            throw new GenerationDocumentsException("La génération du projet " + (lettre ? "de lettre d'invitation" : "d'avis spécifique")
                    + " a échoué : " + e.getMessage(), e);
        }
        List<Produit> produits = new ArrayList<>();
        for (GenerateurDocumentsFiche.Fichier fi : fichiers) {
            produits.add(new Produit(type, fi.extension(), nomFichier(type, etat.getRefeDossier(), etat.getIdDetail(), null, etat.getVersion(),
                    fi.extension()), fi.contenu(), null));
        }
        return produits;
    }

    // ------------------------------------------------------------------ estimatifs (⚠️ V117, 2026-10-14, Q11 §B3)

    /**
     * ⚠️ V117 — les estimatifs d'une version validée : pour chaque lot qui a des articles, un PDF « Bordereau des prix estimatifs »
     * (fournitures) ou « Devis quantitatif et estimatif » (travaux, par série avec sous-totaux) : articles, quantité estimée (le maximum
     * d'un marché à commandes), prix unitaire estimé, montant, total HT face au montant du lot au plan. Vide hors fournitures et
     * travaux, ou si un article n'a pas de prix (le bilan l'interdit à la validation). 500 nommé si le rendu échoue.
     */
    public List<Produit> produireEstimatifs(FicheMarcheDto etat, List<BesoinFiche.Article> articles, LocalDateTime validation) {
        boolean travaux = cnm.prs.enums.CategorieDao.TRAVAUX.name().equals(etat.getCategorie());
        boolean fournitures = cnm.prs.enums.CategorieDao.FOURNITURES_SERVICES.name().equals(etat.getCategorie());
        if (!travaux && !fournitures || articles.isEmpty() || articles.stream().anyMatch(a -> a.prixUnitaireEstime() == null)) {
            return List.of();
        }
        boolean aCommande = cnm.prs.enums.TypeMarcheDao.A_COMMANDE.name().equals(etat.getTypeMarche());
        Map<Integer, java.math.BigDecimal> montantsPlan = new java.util.HashMap<>();
        if (etat.getEstimations() != null) {
            etat.getEstimations().forEach(e -> montantsPlan.put(e.lot(), e.montantPlan()));
        }
        String type = travaux ? TYPE_DQE_ESTIMATIF : TYPE_BORDEREAU_ESTIMATIF;
        Map<Integer, List<BesoinFiche.Article>> parLot = new java.util.TreeMap<>(java.util.Comparator.nullsFirst(Integer::compare));
        articles.forEach(a -> parLot.computeIfAbsent(a.lot(), k -> new ArrayList<>()).add(a));
        List<Produit> produits = new ArrayList<>();
        for (Map.Entry<Integer, List<BesoinFiche.Article>> e : parLot.entrySet()) {
            DocumentLibre doc = estimatif(etat, e.getKey(), e.getValue(), aCommande, travaux, montantsPlan.get(e.getKey()), validation);
            List<GenerateurDocumentsFiche.Fichier> fichiers;
            try {
                fichiers = generateur.generer(doc);
            } catch (RuntimeException ex) {
                throw new GenerationDocumentsException("La génération " + (travaux ? "du devis quantitatif et estimatif" : "du bordereau des prix "
                        + "estimatifs") + (e.getKey() == null ? "" : " du lot " + e.getKey()) + " a échoué : " + ex.getMessage(), ex);
            }
            for (GenerateurDocumentsFiche.Fichier fi : fichiers) {
                if ("pdf".equals(fi.extension())) {
                    produits.add(new Produit(type, "pdf", nomFichier(type, etat.getRefeDossier(), etat.getIdDetail(), e.getKey(),
                            etat.getVersion(), "pdf"), fi.contenu(), e.getKey()));
                }
            }
        }
        return produits;
    }

    static DocumentLibre estimatif(FicheMarcheDto etat, Integer lot, List<BesoinFiche.Article> articles, boolean aCommande, boolean travaux,
            java.math.BigDecimal montantPlan, LocalDateTime validation) {
        List<DocumentLibre.Element> els = new ArrayList<>();
        els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.TITRE, travaux ? "DEVIS QUANTITATIF ET ESTIMATIF" : "BORDEREAU DES PRIX ESTIMATIFS"));
        els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.CENTRE, "Document interne de l'autorité contractante — non publié"));
        String numero = etat.getValeurs() == null ? null : etat.getValeurs().get(CHAMP_NUMERO_DAO);
        if (numero != null && !numero.isBlank()) {
            els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.CENTRE, "Appel d'offres n° " + numero.trim()));
        }
        if (etat.getDesignationMarche() != null) {
            els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.CENTRE, etat.getDesignationMarche()));
        }
        if (lot != null) {
            els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.SOUS_TITRE, "Lot " + lot));
        }
        els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.VIDE, ""));
        List<List<List<String>>> rangees = new ArrayList<>();
        rangees.add(List.of(List.of(travaux ? "N° de prix" : "N°"), List.of("Désignation"), List.of("Unité"),
                List.of(aCommande ? "Quantité maximum" : "Quantité"), List.of("Prix unitaire estimé HT"), List.of("Montant HT")));
        java.math.BigDecimal total = java.math.BigDecimal.ZERO;
        Map<String, List<BesoinFiche.Article>> series = new LinkedHashMap<>();
        articles.forEach(a -> series.computeIfAbsent(travaux && a.serie() != null ? a.serie() : "", k -> new ArrayList<>()).add(a));
        for (Map.Entry<String, List<BesoinFiche.Article>> s : series.entrySet()) {
            boolean enSerie = travaux && !s.getKey().isEmpty();
            if (enSerie) {
                String libelle = s.getValue().stream().map(BesoinFiche.Article::serieLibelle).filter(java.util.Objects::nonNull).findFirst().orElse("");
                rangees.add(List.of(List.of("Série " + s.getKey()), List.of(libelle), List.of(""), List.of(""), List.of(""), List.of("")));
            }
            java.math.BigDecimal sousTotal = java.math.BigDecimal.ZERO;
            for (BesoinFiche.Article a : s.getValue()) {
                java.math.BigDecimal m = a.montantEstime(aCommande);
                sousTotal = sousTotal.add(m);
                rangees.add(List.of(List.of(travaux && a.numeroPrix() != null ? a.numeroPrix() : String.valueOf(a.ordre())),
                        List.of(a.designation()), List.of(a.unite() == null ? "" : a.unite()),
                        List.of(ValeursPpmService.montant(a.quantiteEstimee(aCommande))), List.of(ValeursPpmService.montant(a.prixUnitaireEstime())),
                        List.of(ValeursPpmService.montant(m))));
            }
            if (enSerie) {
                rangees.add(List.of(List.of(""), List.of("Sous-total série " + s.getKey()), List.of(""), List.of(""), List.of(""),
                        List.of(ValeursPpmService.montant(sousTotal))));
            }
            total = total.add(sousTotal);
        }
        rangees.add(List.of(List.of(""), List.of("TOTAL HT"), List.of(""), List.of(""), List.of(""),
                List.of(ValeursPpmService.montant(total.setScale(0, java.math.RoundingMode.HALF_UP)))));
        els.add(new DocumentLibre.Tableau(6, rangees));
        els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.VIDE, ""));
        els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.PARA, "Montant total estimé hors taxes : "
                + MontantEnLettres.ariary(total.setScale(0, java.math.RoundingMode.HALF_UP)) + " ("
                + ValeursPpmService.montant(total.setScale(0, java.math.RoundingMode.HALF_UP)) + " Ar)."));
        els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.PARA, montantPlan == null
                ? "Le plan de passation ne porte pas de montant pour " + (lot == null ? "ce marché." : "ce lot.")
                : "Montant " + (lot == null ? "du marché" : "du lot") + " au plan de passation : " + ValeursPpmService.montant(montantPlan) + " Ar HT."));
        if (aCommande) {
            els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.PARA, "Marché à commandes : le montant est estimé sur les quantités maximum."));
        }
        String pied = "Produit par l'application le " + validation.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm"))
                + " à la validation de la version " + etat.getVersion() + " de la fiche ; la validation tracée de la fiche vaut signature.";
        return new DocumentLibre(travaux ? TYPE_DQE_ESTIMATIF : TYPE_BORDEREAU_ESTIMATIF, lot, els, pied);
    }

    /** ⚠️ V117 — les estimatifs d'une version (lus avec la fiche, jamais par un candidat). */
    @Transactional(readOnly = true)
    public List<DocumentFicheDto> listerEstimatifs(FicheMarche fiche) {
        if (fiche == null || fiche.getIdFiche() == null) {
            return List.of();
        }
        return documentRepository.findByIdFicheOrderByIdDocumentAsc(fiche.getIdFiche()).stream()
                .filter(d -> TYPES_ESTIMATIF.contains(d.getType()))
                .map(d -> new DocumentFicheDto(d.getIdDocument(), d.getType(), (TYPE_DQE_ESTIMATIF.equals(d.getType())
                        ? "Devis quantitatif et estimatif" : "Bordereau des prix estimatifs") + (d.getLot() == null ? "" : " — lot " + d.getLot()),
                        d.getExtension(), d.getNomFichier(), d.getTailleOctets(), d.getDateGeneration(), fiche.getNumeroVersion(), d.getLot()))
                .toList();
    }

    /** ⚠️ R3c — le modèle d'un sigle ({@code AVIS-F}, {@code LETTRE-AOR}…), ou {@code null}. */
    public FichierCommande.Modele modele(String sigle) {
        return modelesDao.modele(sigle);
    }

    /**
     * ⚠️ R3c (manuel révisé, lot 1, §B3) — le document de publication rendu sur un état (version courante ou validée), sans
     * information de publication ni destinataire, pour la relecture des mentions ({@link MentionsPublication}). Rien n'est écrit.
     */
    public DocumentLibre rendrePublication(FicheMarcheDto etat, String sigle, boolean lettre) {
        FichierCommande.Modele modele = modelesDao.modele(sigle);
        if (modele == null) {
            throw new GenerationDocumentsException("Aucun modèle " + sigle + ".", null);
        }
        Map<String, ChampFicheMarche> parCode = new LinkedHashMap<>();
        champRepository.findByActifTrueOrderByCodeRubriqueAscRangAsc().forEach(c -> parCode.put(c.getCode(), c));
        return FormulairesCandidat.rendreModele(lettre ? TYPE_LETTRE : TYPE_AVIS, null, etat, parCode, modele, null, parametresDocuments());
    }

    /** Le projet, ou rien (journal applicatif) : son échec ne bloque ni la validation de la fiche ni la création du dossier. */
    public List<Produit> projetOuRien(FicheMarcheDto etat, LocalDateTime validation) {
        try {
            return produireProjet(etat, validation);
        } catch (RuntimeException e) {
            log.warn("[FICHE_MARCHE] projet de publication de la version {} du DMC {} non produit : {}", etat.getVersion(), etat.getIdDmc(),
                    e.getMessage());
            return List.of();
        }
    }

    /**
     * Une version validée avant R3b n'a pas de projet : il est produit et enregistré au premier besoin (création ou rattachement du
     * dossier), pour être joint d'office avec les autres documents.
     */
    public void assurerProjet(FicheMarche validee, FicheMarcheDto etat) {
        if (validee == null || etat == null || !StatutFicheMarche.VALIDEE.name().equals(validee.getStatut())) {
            return;
        }
        boolean present = documentRepository.findByIdFicheOrderByIdDocumentAsc(validee.getIdFiche()).stream()
                .anyMatch(d -> TYPES_PROJET.contains(d.getType()));
        if (!present) {
            enregistrer(validee.getIdFiche(), projetOuRien(etat, validee.getDateValidation()), LocalDateTime.now());
        }
    }

    /**
     * ⚠️ Avis spécifique (et ⚠️ 2026-10-01 lettre d'invitation) — rattache une publication à la version validée qu'elle
     * rend, avec la trace de ce qui a été saisi à l'impression.
     */
    public java.util.Set<Integer> enregistrerAvis(Integer idFiche, List<Produit> produits, LocalDateTime date, String publicationJson) {
        java.util.Set<Integer> ids = new java.util.HashSet<>();
        for (Produit p : produits) {
            ids.add(documentRepository.save(new DocumentFicheMarche(null, idFiche, p.type(), p.extension(), p.nomFichier(),
                    (long) p.contenu().length, empreinte(p.contenu()), date, p.contenu(), p.lot(), publicationJson)).getIdDocument());
        }
        return ids;
    }

    /**
     * ⚠️ Avis spécifique — les avis (et ⚠️ 2026-10-01 les lettres d'invitation) produits sur les versions données, du plus
     * récent au plus ancien, avec ce qui a été saisi à l'impression.
     */
    @Transactional(readOnly = true)
    public List<DocumentFicheDto> listerAvis(List<FicheMarche> versions) {
        Map<Integer, Integer> numeros = new LinkedHashMap<>();
        versions.forEach(v -> numeros.put(v.getIdFiche(), v.getNumeroVersion()));
        if (numeros.isEmpty()) {
            return List.of();
        }
        return documentRepository.findByIdFicheInAndTypeInOrderByIdDocumentDesc(numeros.keySet(), TYPES_PUBLICATION).stream()
                .map(d -> new DocumentFicheDto(d.getIdDocument(), d.getType(), SelectionDocumentsFiche.titre(d.getType()),
                        d.getExtension(), d.getNomFichier(), d.getTailleOctets(), d.getDateGeneration(),
                        numeros.get(d.getIdFiche()), d.getLot(), publication(d.getPublication())))
                .toList();
    }

    /** ⚠️ R4 (manuel révisé, §B5) — les projets d'avis et de lettre (R3b) des versions données, du plus récent au plus ancien. */
    @Transactional(readOnly = true)
    public List<DocumentFicheDto> listerProjets(List<FicheMarche> versions) {
        Map<Integer, Integer> numeros = new LinkedHashMap<>();
        versions.forEach(v -> numeros.put(v.getIdFiche(), v.getNumeroVersion()));
        if (numeros.isEmpty()) {
            return List.of();
        }
        return documentRepository.findByIdFicheInAndTypeInOrderByIdDocumentDesc(numeros.keySet(), TYPES_PROJET).stream()
                .map(d -> new DocumentFicheDto(d.getIdDocument(), d.getType(),
                        TYPE_AVIS_PROJET.equals(d.getType()) ? "Projet d'avis spécifique d'appel d'offres" : "Projet de lettre d'invitation",
                        d.getExtension(), d.getNomFichier(), d.getTailleOctets(), d.getDateGeneration(), numeros.get(d.getIdFiche()), d.getLot(), null))
                .toList();
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * La trace JSON d'une publication, relue ; illisible ou absente : {@code null}. ⚠️ 2026-10-01 — des valeurs qui ne
     * sont pas toutes du texte (la liste des candidats d'une lettre d'invitation, son rang).
     */
    static Map<String, Object> publication(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (java.io.IOException e) {
            return null;
        }
    }

    /** La trace JSON des informations de publication d'un avis. */
    static String publicationJson(Map<String, ?> publication) {
        try {
            return JSON.writeValueAsString(publication);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Rattache les documents produits à la version validée. */
    public void enregistrer(Integer idFiche, List<Produit> produits, LocalDateTime date) {
        for (Produit p : produits) {
            documentRepository.save(new DocumentFicheMarche(null, idFiche, p.type(), p.extension(), p.nomFichier(),
                    (long) p.contenu().length, empreinte(p.contenu()), date, p.contenu(), p.lot(), null));
        }
    }

    /** Les documents d'une version (fiche). */
    @Transactional(readOnly = true)
    public List<DocumentFicheDto> lister(FicheMarche fiche) {
        return lister(fiche, null);
    }

    /** ⚠️ Lot D2 (2026-09-29) — avec la catégorie de la fiche, qui fait le titre du CCAP des fournitures. */
    @Transactional(readOnly = true)
    public List<DocumentFicheDto> lister(FicheMarche fiche, String categorie) {
        if (fiche == null || fiche.getIdFiche() == null || !StatutFicheMarche.VALIDEE.name().equals(fiche.getStatut())) {
            return List.of();
        }
        List<DocumentFicheMarche> tous = documentRepository.findByIdFicheOrderByIdDocumentAsc(fiche.getIdFiche());
        // ⚠️ 2026-10-06 (DAO complet, « le DAO complet seul ») — quand il existe, il remplace les documents séparés ; les classeurs
        // (xlsx : bordereau, DQE, conformité) restent servis à côté.
        boolean complet = tous.stream().anyMatch(d -> DaoCompletService.TYPE.equals(d.getType()));
        return tous.stream()
                .filter(d -> !complet || DaoCompletService.TYPE.equals(d.getType()) || "xlsx".equals(d.getExtension()))
                .filter(d -> !TYPES_PUBLICATION.contains(d.getType()))   // ⚠️ 2026-09-30 — avis et lettres se listent à part (listerAvis)
                .filter(d -> !TYPES_PROJET.contains(d.getType()))   // ⚠️ R3b — le projet n'est pas un document du DAO (route …/projet)
                .filter(d -> !TYPES_ESTIMATIF.contains(d.getType()))   // ⚠️ V117 — l'estimatif n'est jamais publié (listerEstimatifs)
                // ⚠️ 2026-10-06 (recette, C6) — le DAO complet en tête, avant les classeurs ; l'ordre est stable pour le reste.
                .sorted(java.util.Comparator.comparing((DocumentFicheMarche d) -> !DaoCompletService.TYPE.equals(d.getType())))
                .map(d -> new DocumentFicheDto(d.getIdDocument(), d.getType(),
                        DaoCompletService.TYPE.equals(d.getType()) ? (cnm.prs.enums.CategorieDao.PRESTATIONS_INTELLECTUELLES.name().equals(categorie)
                                ? "Dossier de consultation complet" : "Dossier d'appel d'offres complet")
                                : SelectionDocumentsFiche.titre(d.getType(), d.getLot(), fiche.getTypeMarche(), categorie), d.getExtension(), d.getNomFichier(),
                        d.getTailleOctets(), d.getDateGeneration(), fiche.getNumeroVersion(), d.getLot()))
                .toList();
    }

    /**
     * Joint au dossier les PDF de la dernière version validée de la fiche du DMC. Selon le statut du dossier :
     * <ul>
     *   <li>constitution ou attente de pièces ({@code BROUILLON}, {@code SOUMIS}, {@code EN_ATTENTE_COMPLEMENTS_DEPOT},
     *       {@code EN_ATTENTE_PIECES}) : les pièces d'une version précédente sont détachées, les nouvelles jointes ;</li>
     *   <li>rectification ({@code EN_ATTENTE_DECISION_PRMP}, et ⚠️ lot C : {@code EN_ATTENTE_PIECES}) : les nouvelles sont
     *       jointes en <em>version corrigée</em>, les précédentes conservées — comme toute pièce déposée pendant la
     *       rectification ; en attente de pièces, rattachées à la dernière lettre de renvoi signée (compléments) ;</li>
     *   <li>dossier en examen ou au-delà : rien ne change sous les yeux de la Commission.</li>
     * </ul>
     * Sans type de pièce {@code DAO_COMPLET} au référentiel : rien n'est joint (journal applicatif).
     *
     * @return le nombre de pièces jointes par cet appel (⚠️ lot C : « P pièce(s) remplacée(s) » du journal)
     */
    public int joindre(Integer idDossier, Long idDmc) {
        Dossier dossier = dossierRepository.findById(idDossier).orElse(null);
        FicheMarche fiche = ficheRepository.findFirstByIdDmcOrderByNumeroVersionDesc(idDmc).orElse(null);
        if (dossier == null || fiche == null) {
            return 0;
        }
        // La dernière version VALIDÉE (une révision ouverte n'a pas de documents).
        FicheMarche validee = StatutFicheMarche.VALIDEE.name().equals(fiche.getStatut()) ? fiche
                : ficheRepository.findByIdDmcOrderByNumeroVersionAsc(idDmc).stream()
                        .filter(f -> StatutFicheMarche.VALIDEE.name().equals(f.getStatut()))
                        .reduce((a, b) -> b).orElse(null);
        if (validee == null) {
            return 0;
        }
        List<DocumentFicheMarche> tousPdf = documentRepository.findByIdFicheOrderByIdDocumentAsc(validee.getIdFiche()).stream()
                .filter(d -> "pdf".equals(d.getExtension()))
                .filter(d -> !TYPES_PUBLICATION.contains(d.getType()))   // ⚠️ 2026-09-30 — ni l'avis ni les lettres ne sont des pièces du DAO
                .toList();
        // ⚠️ R3b (manuel révisé, lot 1, §B3) — le projet d'avis (ou de lettre) est joint sous son propre type de pièce, à côté du DAO.
        List<DocumentFicheMarche> projets = tousPdf.stream().filter(d -> TYPES_PROJET.contains(d.getType())).toList();
        // ⚠️ V117 (Q11, §B3) — les estimatifs (bordereau, DQE) sont joints sous leurs propres pièces, à côté du DAO.
        List<DocumentFicheMarche> estimatifs = tousPdf.stream().filter(d -> TYPES_ESTIMATIF.contains(d.getType())).toList();
        List<DocumentFicheMarche> pdfs = tousPdf.stream().filter(d -> !TYPES_PROJET.contains(d.getType()) && !TYPES_ESTIMATIF.contains(d.getType())).toList();
        // ⚠️ 2026-10-06 (DAO complet) — quand il existe, son PDF seul est joint au dossier, à la place des documents séparés.
        if (pdfs.stream().anyMatch(d -> DaoCompletService.TYPE.equals(d.getType()))) {
            pdfs = pdfs.stream().filter(d -> DaoCompletService.TYPE.equals(d.getType())).toList();
        }
        Map<Integer, Integer> typeParDocument = new LinkedHashMap<>();
        if (!pdfs.isEmpty()) {
            TypePieceJointe type = typePieceRepository.findFirstByCode(CODE_TYPE_PIECE).orElse(null);
            if (type == null) {
                log.warn("[FICHE_MARCHE] aucun type de pièce de code {} : documents non joints au dossier {}",
                        CODE_TYPE_PIECE, idDossier);
            } else {
                pdfs.forEach(d -> typeParDocument.put(d.getIdDocument(), type.getIdTypePiece()));
            }
        }
        for (DocumentFicheMarche d : projets) {
            String code = TYPE_AVIS_PROJET.equals(d.getType()) ? CODE_PIECE_AVIS : CODE_PIECE_LETTRE;
            TypePieceJointe type = typePieceRepository.findFirstByCode(code).orElse(null);
            if (type == null) {
                log.warn("[FICHE_MARCHE] aucun type de pièce de code {} : projet {} non joint au dossier {}", code, d.getNomFichier(), idDossier);
            } else {
                typeParDocument.put(d.getIdDocument(), type.getIdTypePiece());
            }
        }
        for (DocumentFicheMarche d : estimatifs) {
            String code = TYPE_DQE_ESTIMATIF.equals(d.getType()) ? CODE_PIECE_DQE : CODE_PIECE_BORDEREAU;
            TypePieceJointe type = typePieceRepository.findFirstByCode(code).orElse(null);
            if (type == null) {
                log.warn("[FICHE_MARCHE] aucun type de pièce de code {} : estimatif {} non joint au dossier {}", code, d.getNomFichier(), idDossier);
            } else {
                typeParDocument.put(d.getIdDocument(), type.getIdTypePiece());
            }
        }
        if (typeParDocument.isEmpty()) {
            return 0;   // version validée avant le lot 2 : aucun document à joindre
        }
        List<DocumentFicheMarche> aJoindre = java.util.stream.Stream.of(pdfs, projets, estimatifs).flatMap(List::stream)
                .filter(d -> typeParDocument.containsKey(d.getIdDocument())).toList();
        List<PieceJointeDossier> actuelles = pieceRepository.findByIdDossierAndIdDocumentFicheIsNotNull(idDossier);
        Set<Integer> dejaJoints = new java.util.HashSet<>();
        actuelles.forEach(p -> dejaJoints.add(p.getIdDocumentFiche()));
        if (aJoindre.stream().allMatch(d -> dejaJoints.contains(d.getIdDocument()))) {
            return 0;
        }
        String statut = dossier.getStatut();
        boolean rectification = STATUTS_RECTIFICATION.contains(statut);
        if (!rectification && !STATUTS_REMPLACEMENT.contains(statut)) {
            log.info("[FICHE_MARCHE] dossier {} au statut {} : documents de la version {} non joints (dossier en examen)",
                    idDossier, statut, validee.getNumeroVersion());
            return 0;
        }
        if (!rectification) {
            pieceRepository.deleteAll(actuelles);
        }
        // ⚠️ Lot C (§B2/§B3) — en attente de pièces, les documents de la révision SONT les compléments : rattachés à la
        // dernière lettre de renvoi signée, comme une pièce déposée après renvoi.
        boolean attentePieces = StatutDossier.EN_ATTENTE_PIECES.name().equals(statut);
        Integer idLettre = !attentePieces ? null : lettreRenvoiRepository
                .findFirstByIdDossierAndStatutOrderByIdLettreDesc(idDossier, cnm.prs.enums.StatutLettreRenvoi.SIGNE.name())
                .map(cnm.prs.entity.LettreRenvoi::getIdLettre).orElse(null);
        LocalDateTime maintenant = LocalDateTime.now();
        int jointes = 0;
        for (DocumentFicheMarche d : aJoindre) {
            if (dejaJoints.contains(d.getIdDocument())) {
                continue;
            }
            PieceJointeDossier p = new PieceJointeDossier();
            p.setIdDossier(idDossier);
            p.setIdTypePiece(typeParDocument.get(d.getIdDocument()));
            p.setNomFichier(d.getNomFichier());
            p.setContenu(d.getContenu());
            p.setFormat("PDF");
            p.setTaille(d.getTailleOctets());
            p.setDateUpload(maintenant);
            p.setApresLettreRenvoi(attentePieces);
            p.setIdLettre(idLettre);
            p.setVersionCorrigee(rectification ? Boolean.TRUE : null);
            p.setIdDocumentFiche(d.getIdDocument());
            pieceRepository.save(p);
            jointes++;
        }
        return jointes;
    }

    /** Détache du dossier les pièces produites par sa fiche (la fiche en est détachée). */
    public void detacher(Integer idDossier) {
        pieceRepository.deleteAll(pieceRepository.findByIdDossierAndIdDocumentFicheIsNotNull(idDossier));
    }

    /**
     * {@code DPAO_00001-MTP-PPM-AGPM-2026_302873_v2.docx} : type, référence du plan (caractères hors
     * {@code [A-Za-z0-9-]} remplacés par des tirets), ligne, version.
     */
    static String nomFichier(String type, String refePlan, Integer idDetail, Integer version, String extension) {
        return nomFichier(type, refePlan, idDetail, null, version, extension);
    }

    /** ⚠️ 2026-09-25 — un document établi par lot porte son rang : {@code AE_<plan>_302873_lot2_v1.pdf}. */
    static String nomFichier(String type, String refePlan, Integer idDetail, Integer lot, Integer version, String extension) {
        // ⚠️ 2026-10-06 (contre-recette du DAO complet) — les lettres accentuées sont translittérées (é → e) avant le remplacement
        // des autres signes par des tirets : « Numéro » donne « Numero », plus « Num-ro ».
        String refe = refePlan == null || refePlan.isBlank() ? "sans-reference"
                : java.text.Normalizer.normalize(refePlan.trim(), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                        .replaceAll("[^A-Za-z0-9-]+", "-").replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
        return type + "_" + refe + "_" + idDetail + (lot == null ? "" : "_lot" + lot) + "_v" + version + "." + extension;
    }

    private static String empreinte(byte[] contenu) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenu));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
