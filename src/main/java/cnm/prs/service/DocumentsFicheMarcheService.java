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

    /** Code stable du type de pièce « Dossier d'appel d'offres complet » (V38). */
    public static final String CODE_TYPE_PIECE = "DAO_COMPLET";
    /**
     * ⚠️ 2026-09-30 — le type des documents de l'avis spécifique d'appel d'offres : imprimés à la demande après le PV,
     * rattachés à la version validée qu'ils rendent, jamais joints au dossier comme pièce du DAO.
     */
    public static final String TYPE_AVIS = "AVIS";
    /** ⚠️ 2026-10-01 — le bloc de signature de l'avis : ses trois derniers paragraphes (lieu et date, qualité, nom). */
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
    /** ⚠️ Lot C (2026-09-27) — la lettre de renvoi à laquelle rattacher les documents d'une révision (compléments). */
    private final cnm.prs.repository.LettreRenvoiRepository lettreRenvoiRepository;

    public DocumentsFicheMarcheService(DocumentFicheMarcheRepository documentRepository,
            GenerateurDocumentsFiche generateur, ChampFicheMarcheRepository champRepository,
            BlocFicheMarcheRepository blocRepository, RubriqueFicheMarcheRepository rubriqueRepository,
            FicheMarcheRepository ficheRepository, DossierRepository dossierRepository,
            PieceJointeDossierRepository pieceRepository, TypePieceJointeRepository typePieceRepository,
            GenerateurClasseursFiche classeurs, ParametreService parametres, ModelesCandidat modelesCandidat,
            cnm.prs.repository.LettreRenvoiRepository lettreRenvoiRepository, ModelesDao modelesDao) {
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
        DocumentFicheModele liste = SelectionDocumentsFiche.listeFournitures(etat, articles, validation);
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
            for (GenerateurDocumentsFiche.Fichier f : fichiers) {
                produits.add(new Produit(modele.type(), f.extension(), nomFichier(modele.type(), etat.getRefeDossier(),
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
        for (String type : List.of("BP", "TC")) {
            for (Integer lot : lots) {
                List<BesoinFiche.Article> duLot = articles.stream().filter(a -> java.util.Objects.equals(a.lot(), lot)).toList();
                if (duLot.isEmpty()) {
                    continue;
                }
                byte[] contenu;
                try {
                    contenu = "BP".equals(type)
                            ? classeurs.bordereau(etat.getRefeDossier(), etat.getDesignationMarche(), lot, duLot, aCommande, tva)
                            : classeurs.conformite(etat.getRefeDossier(), etat.getDesignationMarche(), lot, duLot);
                } catch (RuntimeException e) {
                    throw new GenerationDocumentsException("La génération du document « "
                            + SelectionDocumentsFiche.titre(type, lot) + " » a échoué : la version n'est pas validée. "
                            + e.getMessage(), e);
                }
                produits.add(new Produit(type, "xlsx", nomFichier(type, etat.getRefeDossier(), etat.getIdDetail(), lot,
                        etat.getVersion(), "xlsx"), contenu, lot));
            }
        }
        return produits;
    }

    /**
     * ⚠️ Avis spécifique d'appel d'offres (demande front du 2026-09-30, §B1-§B3) — l'avis rendu depuis le modèle de la
     * catégorie ({@link ModelesDao#sigleAvis}), sur l'état figé de la version validée, avec les informations de publication
     * déjà mises en forme ({@code date-publication}, {@code jmp-numero}, {@code jmp-date}, {@code supports}). Un .docx et
     * un .pdf ; le nom porte l'horodatage d'impression, chaque impression produisant une nouvelle paire.
     */
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
            doc = FormulairesCandidat.rendreModele(TYPE_AVIS, null, etat, parCode, modele, impression, jetons)
                    .finGardeeEnsemble(BLOC_SIGNATURE_AVIS);
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

    /** ⚠️ Avis spécifique — rattache l'avis à la version validée qu'il rend, avec la trace des informations de publication. */
    public java.util.Set<Integer> enregistrerAvis(Integer idFiche, List<Produit> produits, LocalDateTime date, String publicationJson) {
        java.util.Set<Integer> ids = new java.util.HashSet<>();
        for (Produit p : produits) {
            ids.add(documentRepository.save(new DocumentFicheMarche(null, idFiche, p.type(), p.extension(), p.nomFichier(),
                    (long) p.contenu().length, empreinte(p.contenu()), date, p.contenu(), p.lot(), publicationJson)).getIdDocument());
        }
        return ids;
    }

    /**
     * ⚠️ Avis spécifique — les avis produits sur les versions données, du plus récent au plus ancien, avec leurs
     * informations de publication.
     */
    @Transactional(readOnly = true)
    public List<DocumentFicheDto> listerAvis(List<FicheMarche> versions) {
        Map<Integer, Integer> numeros = new LinkedHashMap<>();
        versions.forEach(v -> numeros.put(v.getIdFiche(), v.getNumeroVersion()));
        if (numeros.isEmpty()) {
            return List.of();
        }
        return documentRepository.findByIdFicheInAndTypeOrderByIdDocumentDesc(numeros.keySet(), TYPE_AVIS).stream()
                .map(d -> new DocumentFicheDto(d.getIdDocument(), d.getType(), SelectionDocumentsFiche.titre(TYPE_AVIS),
                        d.getExtension(), d.getNomFichier(), d.getTailleOctets(), d.getDateGeneration(),
                        numeros.get(d.getIdFiche()), d.getLot(), publication(d.getPublication())))
                .toList();
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    /** La trace JSON d'un avis, relue ; illisible ou absente : {@code null}. */
    static Map<String, String> publication(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, String>>() { });
        } catch (java.io.IOException e) {
            return null;
        }
    }

    /** La trace JSON des informations de publication d'un avis. */
    static String publicationJson(Map<String, String> publication) {
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
        return documentRepository.findByIdFicheOrderByIdDocumentAsc(fiche.getIdFiche()).stream()
                .filter(d -> !TYPE_AVIS.equals(d.getType()))   // ⚠️ 2026-09-30 — les avis se listent à part (listerAvis)
                .map(d -> new DocumentFicheDto(d.getIdDocument(), d.getType(),
                        SelectionDocumentsFiche.titre(d.getType(), d.getLot(), fiche.getTypeMarche(), categorie), d.getExtension(), d.getNomFichier(),
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
        List<DocumentFicheMarche> pdfs = documentRepository.findByIdFicheOrderByIdDocumentAsc(validee.getIdFiche()).stream()
                .filter(d -> "pdf".equals(d.getExtension()))
                .filter(d -> !TYPE_AVIS.equals(d.getType()))   // ⚠️ 2026-09-30 — l'avis n'est pas une pièce du DAO
                .toList();
        if (pdfs.isEmpty()) {
            return 0;   // version validée avant le lot 2 : aucun document à joindre
        }
        TypePieceJointe type = typePieceRepository.findFirstByCode(CODE_TYPE_PIECE).orElse(null);
        if (type == null) {
            log.warn("[FICHE_MARCHE] aucun type de pièce de code {} : documents non joints au dossier {}",
                    CODE_TYPE_PIECE, idDossier);
            return 0;
        }
        List<PieceJointeDossier> actuelles = pieceRepository.findByIdDossierAndIdDocumentFicheIsNotNull(idDossier);
        Set<Integer> dejaJoints = new java.util.HashSet<>();
        actuelles.forEach(p -> dejaJoints.add(p.getIdDocumentFiche()));
        if (pdfs.stream().allMatch(d -> dejaJoints.contains(d.getIdDocument()))) {
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
        for (DocumentFicheMarche d : pdfs) {
            if (dejaJoints.contains(d.getIdDocument())) {
                continue;
            }
            PieceJointeDossier p = new PieceJointeDossier();
            p.setIdDossier(idDossier);
            p.setIdTypePiece(type.getIdTypePiece());
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
        String refe = refePlan == null || refePlan.isBlank() ? "sans-reference"
                : refePlan.trim().replaceAll("[^A-Za-z0-9-]+", "-").replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
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
