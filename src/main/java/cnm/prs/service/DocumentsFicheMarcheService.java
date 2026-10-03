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
        // ⚠️ V59 (2026-10-02, DQE des travaux, §B1.5) — les séries du DQE, jeton {{BESOIN.series}} (découpage du forfait).
        parametresDocuments.putAll(FormulairesCandidat.seriesDuBesoin(articles, nbLots));
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
        for (String type : travaux ? List.of("BP") : List.of("BP", "TC")) {
            for (Integer lot : lots) {
                List<BesoinFiche.Article> duLot = articles.stream().filter(a -> java.util.Objects.equals(a.lot(), lot)).toList();
                if (duLot.isEmpty()) {
                    continue;
                }
                byte[] contenu;
                try {
                    contenu = travaux
                            ? classeurs.bordereauTravaux(etat.getRefeDossier(), etat.getDesignationMarche(), lot, duLot,
                                    aCommande, typePrix == null ? null : String.valueOf(typePrix), tva)
                            : "BP".equals(type)
                            ? classeurs.bordereau(etat.getRefeDossier(), etat.getDesignationMarche(), lot, duLot, aCommande, tva)
                            : classeurs.conformite(etat.getRefeDossier(), etat.getDesignationMarche(), lot, duLot);
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

    /**
     * ⚠️ 2026-10-01 (lot AV-4.1, §B3) — les lettres d'invitation : une paire .docx / .pdf par candidat, dans l'ordre saisi
     * (rang 1, 2…), rendues du modèle de la catégorie ({@link ModelesDao#sigleLettre}) sur l'état figé de la version
     * validée. {@code commun} porte les jetons partagés ({@code LETTRE.lieu}, {@code LETTRE.date}, {@code LETTRE.candidats}),
     * {@code destinataires} celui de chaque lettre ({@code LETTRE.destinataire}). Le nom porte l'horodatage et le rang.
     */
    public Map<Integer, List<Produit>> produireLettres(FicheMarcheDto etat, Map<String, String> commun, List<String> destinataires,
            LocalDateTime impression) {
        FichierCommande.Modele modele = modelesDao.modele(ModelesDao.sigleLettre(etat.getCategorie()));
        if (modele == null) {
            throw new GenerationDocumentsException("Aucun modèle de lettre d'invitation pour la catégorie " + etat.getCategorie() + ".", null);
        }
        Map<String, ChampFicheMarche> parCode = new LinkedHashMap<>();
        champRepository.findByActifTrueOrderByCodeRubriqueAscRangAsc().forEach(c -> parCode.put(c.getCode(), c));
        Map<String, String> parametres = parametresDocuments();
        Map<Integer, List<Produit>> parRang = new LinkedHashMap<>();
        for (int i = 0; i < destinataires.size(); i++) {
            int rang = i + 1;
            List<GenerateurDocumentsFiche.Fichier> fichiers;
            try {
                Map<String, String> jetons = new LinkedHashMap<>(commun);
                jetons.put(FormulairesCandidat.PREFIXE_LETTRE + "destinataire", destinataires.get(i));
                jetons.putAll(parametres);
                fichiers = generateur.generer(FormulairesCandidat.rendreModele(TYPE_LETTRE, null, etat, parCode, modele, impression,
                        jetons).finGardeeEnsemble(BLOC_SIGNATURE_AVIS));
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
        return documentRepository.findByIdFicheOrderByIdDocumentAsc(fiche.getIdFiche()).stream()
                .filter(d -> !TYPES_PUBLICATION.contains(d.getType()))   // ⚠️ 2026-09-30 — avis et lettres se listent à part (listerAvis)
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
                .filter(d -> !TYPES_PUBLICATION.contains(d.getType()))   // ⚠️ 2026-09-30 — ni l'avis ni les lettres ne sont des pièces du DAO
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
