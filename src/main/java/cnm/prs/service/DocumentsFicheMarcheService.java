package cnm.prs.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

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

    /** Statuts où le dossier reçoit les documents d'une nouvelle version à la place des précédents (ceux du dépôt). */
    private static final Set<String> STATUTS_REMPLACEMENT = Set.of(StatutDossier.BROUILLON.name(),
            StatutDossier.SOUMIS.name(), StatutDossier.EN_ATTENTE_COMPLEMENTS_DEPOT.name(),
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

    public DocumentsFicheMarcheService(DocumentFicheMarcheRepository documentRepository,
            GenerateurDocumentsFiche generateur, ChampFicheMarcheRepository champRepository,
            BlocFicheMarcheRepository blocRepository, RubriqueFicheMarcheRepository rubriqueRepository,
            FicheMarcheRepository ficheRepository, DossierRepository dossierRepository,
            PieceJointeDossierRepository pieceRepository, TypePieceJointeRepository typePieceRepository,
            GenerateurClasseursFiche classeurs, ParametreService parametres) {
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
        List<DocumentFicheModele> modeles = new ArrayList<>(SelectionDocumentsFiche.selectionner(etat,
                champRepository.findByActifTrueOrderByCodeRubriqueAscRangAsc(), blocRepository.findAllByOrderByRangAsc(),
                rubriqueRepository.findAllByOrderByCodeBlocAscRangAsc(), validation));
        DocumentFicheModele liste = SelectionDocumentsFiche.listeFournitures(etat, articles, validation);
        if (liste != null) {
            modeles.add(liste);
        }
        List<Produit> produits = new ArrayList<>();
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

    /** Rattache les documents produits à la version validée. */
    public void enregistrer(Integer idFiche, List<Produit> produits, LocalDateTime date) {
        for (Produit p : produits) {
            documentRepository.save(new DocumentFicheMarche(null, idFiche, p.type(), p.extension(), p.nomFichier(),
                    (long) p.contenu().length, empreinte(p.contenu()), date, p.contenu(), p.lot()));
        }
    }

    /** Les documents d'une version (fiche). */
    @Transactional(readOnly = true)
    public List<DocumentFicheDto> lister(FicheMarche fiche) {
        if (fiche == null || fiche.getIdFiche() == null || !StatutFicheMarche.VALIDEE.name().equals(fiche.getStatut())) {
            return List.of();
        }
        return documentRepository.findByIdFicheOrderByIdDocumentAsc(fiche.getIdFiche()).stream()
                .map(d -> new DocumentFicheDto(d.getIdDocument(), d.getType(),
                        SelectionDocumentsFiche.titre(d.getType(), d.getLot()), d.getExtension(), d.getNomFichier(),
                        d.getTailleOctets(), d.getDateGeneration(), fiche.getNumeroVersion(), d.getLot()))
                .toList();
    }

    /**
     * Joint au dossier les PDF de la dernière version validée de la fiche du DMC. Selon le statut du dossier :
     * <ul>
     *   <li>constitution ou attente de pièces ({@code BROUILLON}, {@code SOUMIS}, {@code EN_ATTENTE_COMPLEMENTS_DEPOT},
     *       {@code EN_ATTENTE_PIECES}) : les pièces d'une version précédente sont détachées, les nouvelles jointes ;</li>
     *   <li>rectification ({@code EN_ATTENTE_DECISION_PRMP}) : les nouvelles sont jointes en <em>version corrigée</em>, les
     *       précédentes conservées — comme toute pièce déposée pendant la rectification ;</li>
     *   <li>dossier en examen ou au-delà : rien ne change sous les yeux de la Commission.</li>
     * </ul>
     * Sans type de pièce {@code DAO_COMPLET} au référentiel : rien n'est joint (journal applicatif).
     */
    public void joindre(Integer idDossier, Long idDmc) {
        Dossier dossier = dossierRepository.findById(idDossier).orElse(null);
        FicheMarche fiche = ficheRepository.findFirstByIdDmcOrderByNumeroVersionDesc(idDmc).orElse(null);
        if (dossier == null || fiche == null) {
            return;
        }
        // La dernière version VALIDÉE (une révision ouverte n'a pas de documents).
        FicheMarche validee = StatutFicheMarche.VALIDEE.name().equals(fiche.getStatut()) ? fiche
                : ficheRepository.findByIdDmcOrderByNumeroVersionAsc(idDmc).stream()
                        .filter(f -> StatutFicheMarche.VALIDEE.name().equals(f.getStatut()))
                        .reduce((a, b) -> b).orElse(null);
        if (validee == null) {
            return;
        }
        List<DocumentFicheMarche> pdfs = documentRepository.findByIdFicheOrderByIdDocumentAsc(validee.getIdFiche()).stream()
                .filter(d -> "pdf".equals(d.getExtension())).toList();
        if (pdfs.isEmpty()) {
            return;   // version validée avant le lot 2 : aucun document à joindre
        }
        TypePieceJointe type = typePieceRepository.findFirstByCode(CODE_TYPE_PIECE).orElse(null);
        if (type == null) {
            log.warn("[FICHE_MARCHE] aucun type de pièce de code {} : documents non joints au dossier {}",
                    CODE_TYPE_PIECE, idDossier);
            return;
        }
        List<PieceJointeDossier> actuelles = pieceRepository.findByIdDossierAndIdDocumentFicheIsNotNull(idDossier);
        Set<Integer> dejaJoints = new java.util.HashSet<>();
        actuelles.forEach(p -> dejaJoints.add(p.getIdDocumentFiche()));
        if (pdfs.stream().allMatch(d -> dejaJoints.contains(d.getIdDocument()))) {
            return;
        }
        String statut = dossier.getStatut();
        boolean rectification = StatutDossier.EN_ATTENTE_DECISION_PRMP.name().equals(statut);
        if (!rectification && !STATUTS_REMPLACEMENT.contains(statut)) {
            log.info("[FICHE_MARCHE] dossier {} au statut {} : documents de la version {} non joints (dossier en examen)",
                    idDossier, statut, validee.getNumeroVersion());
            return;
        }
        if (!rectification) {
            pieceRepository.deleteAll(actuelles);
        }
        LocalDateTime maintenant = LocalDateTime.now();
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
            p.setApresLettreRenvoi(false);
            p.setVersionCorrigee(rectification ? Boolean.TRUE : null);
            p.setIdDocumentFiche(d.getIdDocument());
            pieceRepository.save(p);
        }
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
