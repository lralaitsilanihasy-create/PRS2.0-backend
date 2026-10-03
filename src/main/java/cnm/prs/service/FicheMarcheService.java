package cnm.prs.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.BilanControlesDto;
import cnm.prs.dto.DocumentFicheDto;
import cnm.prs.dto.FicheMarcheDto;
import cnm.prs.dto.FicheMarcheResumeDto;
import cnm.prs.dto.FicheRattachableDto;
import cnm.prs.dto.VersionFicheDto;
import cnm.prs.entity.ChampFicheMarche;
import cnm.prs.entity.DocumentFicheMarche;
import cnm.prs.entity.DossierMec;
import cnm.prs.entity.FicheMarche;
import cnm.prs.entity.FicheMarcheValeur;
import cnm.prs.entity.Marche;
import cnm.prs.entity.TypeDmc;
import cnm.prs.enums.CategorieDao;
import cnm.prs.enums.FormeMarche;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.SourceChampFiche;
import cnm.prs.enums.StatutFicheMarche;
import cnm.prs.enums.TypeChampFiche;
import cnm.prs.enums.TypeMarcheDao;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ChampsInvalidesException;
import cnm.prs.exception.ErrorResponse;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.BlocFicheMarcheRepository;
import cnm.prs.repository.ChampFicheMarcheRepository;
import cnm.prs.repository.DossierMecRepository;
import cnm.prs.repository.FicheMarcheRepository;
import cnm.prs.repository.FicheMarcheValeurRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.TypeDmcRepository;
import cnm.prs.security.CurrentUser;
import cnm.prs.security.PerimetreDossier;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * ⚠️ <strong>La fiche marché d'un appel d'offres</strong> (demande front du 2026-09-22, §B3 à §B5).
 *
 * <ul>
 *   <li><strong>Une fiche par DMC de type DAO</strong>, versionnée : {@code BROUILLON} (enregistrée bloc par bloc,
 *       cadrage à part) puis {@code VALIDEE} par la PRMP (figée) ; {@code reviser} ouvre la version suivante en
 *       copiant la dernière validée. Avant le premier enregistrement, la fiche est <em>virtuelle</em> : GET la sert
 *       (version 1, brouillon, vide) sans rien écrire ; le premier PUT la crée.</li>
 *   <li><strong>Lecture</strong> : périmètre du dossier de la ligne ({@link PerimetreDossier}). <strong>Écriture</strong>
 *       : PRMP et UGPM propriétaires (le périmètre le vérifie), Administrateur ; mandat actif exigé (VACANCE_PRMP).
 *       <strong>Validation</strong> : la PRMP seule.</li>
 *   <li>{@code valeurs} ne reçoit que des champs de source {@code SAISIE} du bloc visé, actifs, du type de marché,
 *       dont la condition de cadrage est vraie — un champ fermé est <em>ignoré</em>, un champ dérivé (PPM, CADRAGE) ou
 *       inconnu est un 400 nominatif, comme une valeur mal typée. Un obligatoire manquant n'est <em>pas</em> un 400 :
 *       il est bloquant au bilan (on enregistre un brouillon incomplet, on ne valide pas).</li>
 *   <li>Les valeurs PPM sont relues à chaque lecture ({@link ValeursPpmService}), jamais stockées (H7).</li>
 * </ul>
 */
@Service
@Transactional
public class FicheMarcheService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FicheMarcheService.class);

    /** Clés admises du cadrage sans champ reflet (les autres viennent des champs de source CADRAGE). */
    private static final Set<String> CLES_CADRAGE_LIBRES = Set.of("attributaires");

    /**
     * ⚠️ Lot 1c (2026-09-23) — ancienne clé du cadrage : le type de marché se déduit désormais de la forme du marché au
     * plan. Ignorée à l'écriture et retirée à la lecture des cadrages enregistrés avant.
     */
    private static final String CLE_TYPE_MARCHE = "typeMarche";

    /** ⚠️ Lot 5 (2026-09-24, §B5) — clé de cadrage des travaux : le marché comporte-t-il des tranches ? */
    private static final String CLE_TRANCHES = "tranches";

    private final FicheMarcheRepository ficheRepository;
    private final FicheMarcheValeurRepository valeurRepository;
    private final ChampFicheMarcheRepository champRepository;
    private final BlocFicheMarcheRepository blocRepository;
    private final DossierMecRepository dmcRepository;
    /** ⚠️ Lot 1b (2026-09-23) — le dossier soumis que porte le DMC ({@code t_dossier.ID_DMC}). */
    private final cnm.prs.repository.DossierRepository dossierRepository;
    /** ⚠️ Lot 2a (2026-09-23) — production, lecture et jointure des documents générés. */
    private final DocumentsFicheMarcheService documents;
    /** ⚠️ Lot 5 (2026-09-24) — la catégorie de la ligne (nature → catégorie) et son refus. */
    private final DmcService dmcService;
    private final cnm.prs.repository.DocumentFicheMarcheRepository documentRepository;
    private final MarcheRepository marcheRepository;
    private final TypeDmcRepository typeDmcRepository;
    private final PerimetreDossier perimetre;
    private final ValeursPpmService valeursPpm;
    private final DossierIntegriteService dossierIntegrite;
    private final JournalDossierService journal;
    private final ObjectMapper mapper;
    /** ⚠️ V45 (2026-09-25) — le besoin par lot (fournitures) et les paramètres du contrôle du taux de garantie. */
    private final BesoinFiche besoin;
    private final ParametreService parametres;
    /** ⚠️ V50 (2026-09-27, remise électronique) — paramètres internes et responsable de la procédure (état, contexte du bilan). */
    private final ParametresInternesService internes;
    /** ⚠️ 2026-09-28 (contrat-cadre, §B7) — le mandat en vigueur, source du défaut de l'acte de nomination. */
    private final MandatService mandats;
    /** ⚠️ V60 (2026-10-03) — le matériel et le personnel exigés d'une fiche de travaux. */
    private final MoyensFiche moyens;
    /** ⚠️ V61 (2026-10-03) — les pièces de l'offre d'une fiche de travaux. */
    private final PiecesFiche pieces;

    public FicheMarcheService(FicheMarcheRepository ficheRepository, FicheMarcheValeurRepository valeurRepository,
            ChampFicheMarcheRepository champRepository, BlocFicheMarcheRepository blocRepository,
            DossierMecRepository dmcRepository, MarcheRepository marcheRepository, TypeDmcRepository typeDmcRepository,
            PerimetreDossier perimetre, ValeursPpmService valeursPpm, DossierIntegriteService dossierIntegrite,
            JournalDossierService journal, ObjectMapper mapper,
            cnm.prs.repository.DossierRepository dossierRepository, DocumentsFicheMarcheService documents,
            cnm.prs.repository.DocumentFicheMarcheRepository documentRepository, DmcService dmcService,
            BesoinFiche besoin, ParametreService parametres, ParametresInternesService internes, MandatService mandats,
            MoyensFiche moyens, PiecesFiche pieces) {
        this.pieces = pieces;
        this.moyens = moyens;
        this.mandats = mandats;
        this.besoin = besoin;
        this.parametres = parametres;
        this.internes = internes;
        this.dmcService = dmcService;
        this.documents = documents;
        this.documentRepository = documentRepository;
        this.dossierRepository = dossierRepository;
        this.ficheRepository = ficheRepository;
        this.valeurRepository = valeurRepository;
        this.champRepository = champRepository;
        this.blocRepository = blocRepository;
        this.dmcRepository = dmcRepository;
        this.marcheRepository = marcheRepository;
        this.typeDmcRepository = typeDmcRepository;
        this.perimetre = perimetre;
        this.valeursPpm = valeursPpm;
        this.dossierIntegrite = dossierIntegrite;
        this.journal = journal;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------------ lecture

    @Transactional(readOnly = true)
    public FicheMarcheDto lire(Long idDmc) {
        Contexte ctx = contexte(idDmc);
        FicheMarche fiche = ficheRepository.findFirstByIdDmcOrderByNumeroVersionDesc(idDmc).orElseGet(() -> virtuelle(idDmc));
        return toDto(ctx, fiche);
    }

    @Transactional(readOnly = true)
    public BilanControlesDto controler(Long idDmc) {
        return lire(idDmc).getBilanControles();
    }

    @Transactional(readOnly = true)
    public List<VersionFicheDto> versions(Long idDmc) {
        contexte(idDmc);
        return ficheRepository.findByIdDmcOrderByNumeroVersionAsc(idDmc).stream()
                .filter(f -> StatutFicheMarche.VALIDEE.name().equals(f.getStatut()))
                .map(f -> new VersionFicheDto(f.getIdFiche(), f.getNumeroVersion(), f.getStatut(), f.getTypeMarche(),
                        f.getDateCreation(), f.getDateValidation(), f.getValidePar(),
                        valeurRepository.findByIdFiche(f.getIdFiche()).size()))
                .toList();
    }

    @Transactional(readOnly = true)
    public FicheMarcheDto lireVersion(Long idDmc, Integer numero) {
        Contexte ctx = contexte(idDmc);
        FicheMarche fiche = ficheRepository.findByIdDmcAndNumeroVersion(idDmc, numero)
                .orElseThrow(() -> new ResourceNotFoundException("Version " + numero + " introuvable pour le DMC " + idDmc + "."));
        return toDto(ctx, fiche);
    }

    /**
     * ⚠️ Lot 1b (2026-09-23, §B1) — l'état réduit de la fiche d'un DMC, pour le bloc {@code ficheMarche} du dossier
     * qui la porte. <strong>Sans contrôle de périmètre</strong> : n'est appelé que sur un dossier déjà lu par son
     * lecteur, et la fiche se lit « au périmètre du dossier » (B4) — le contrôleur qui lit le dossier voit sa fiche.
     */
    @Transactional(readOnly = true)
    public FicheMarcheResumeDto resume(Long idDmc) {
        return resume(idDmc, null);
    }

    /** ⚠️ Lot C (2026-09-27, §B1) — avec la version que le dossier a soumise ({@code t_dossier.VERSION_FICHE_SOUMISE}). */
    @Transactional(readOnly = true)
    public FicheMarcheResumeDto resume(Long idDmc, Integer versionSoumise) {
        Contexte ctx = contexte(idDmc, false);
        FicheMarche fiche = ficheRepository.findFirstByIdDmcOrderByNumeroVersionDesc(idDmc).orElseGet(() -> virtuelle(idDmc));
        FicheMarcheDto d = toDto(ctx, fiche);
        return new FicheMarcheResumeDto(idDmc, d.getIdDetail(), d.getRefeDossier(), d.getDesignationMarche(),
                d.getTypeMarche(), d.getStatut(), d.getVersion(), d.getBilanControles().nbSaisis(),
                d.getBilanControles().nbAttendus(), versionSoumise, d.getResponsableProcedure(),
                d.getPeutModifierParametresInternes(), d.getParametresInternes(), d.getChampsCalcules());   // V50
    }

    /**
     * ⚠️ Lot 1b (2026-09-23, §B3) — les fiches que l'utilisateur courant peut rattacher à un dossier : dernière version
     * {@code VALIDEE}, DMC sans dossier, ligne dans un plan de son périmètre. Le plus récent d'abord.
     */
    @Transactional(readOnly = true)
    public List<FicheRattachableDto> rattachables() {
        List<FicheRattachableDto> out = new ArrayList<>();
        for (FicheMarche f : ficheRepository.findDernieresValideesSansDossier()) {
            DossierMec dmc = dmcRepository.findById(f.getIdDmc()).orElse(null);
            if (dmc == null) {
                continue;
            }
            Integer idDossierPpm = marcheRepository.findIdDossierByIdDetail(dmc.getIdDetail()).orElse(null);
            if (idDossierPpm == null || !perimetre.estVisible(idDossierPpm)) {
                continue;
            }
            cnm.prs.entity.Marche ligne = marcheRepository.findById(dmc.getIdDetail()).map(valeursPpm::ligneEnVigueur).orElse(null);
            String refe = ligne == null || ligne.getIdDossier() == null ? null
                    : dossierRepository.findById(ligne.getIdDossier()).map(cnm.prs.entity.Dossier::getRefeDossier).orElse(null);
            out.add(new FicheRattachableDto(f.getIdDmc(), dmc.getIdDetail(), refe,
                    ligne == null ? null : ligne.getDesignationMarche(), f.getNumeroVersion(), f.getDateValidation()));
        }
        return out;
    }

    /**
     * ⚠️ Lot 2a (2026-09-23, §B2) — les documents de la version courante (sans {@code version}) ou d'une version donnée,
     * au périmètre de lecture de la fiche. Version non validée : liste vide, jamais 404 ; version inconnue : 404.
     */
    @Transactional(readOnly = true)
    public List<DocumentFicheDto> documents(Long idDmc, Integer version) {
        Contexte ctx = contexte(idDmc);
        FicheMarche fiche = version == null
                ? ficheRepository.findFirstByIdDmcOrderByNumeroVersionDesc(idDmc).orElse(null)
                : ficheRepository.findByIdDmcAndNumeroVersion(idDmc, version).orElseThrow(
                        () -> new ResourceNotFoundException("Version " + version + " introuvable pour le DMC " + idDmc + "."));
        List<DocumentFicheDto> liste = new java.util.ArrayList<>(documents.lister(fiche, ctx.codeCategorie()));
        // ⚠️ 2026-09-30 (avis spécifique, §B4) — les avis imprimés, du plus récent au plus ancien : ceux de toutes les
        // versions sans ?version (ils restent listés pendant une révision ouverte), ceux de la version demandée sinon.
        liste.addAll(documents.listerAvis(version == null ? ficheRepository.findByIdDmcOrderByNumeroVersionAsc(idDmc)
                : fiche == null ? List.of() : List.of(fiche)));
        return liste;
    }

    /**
     * ⚠️ V44 (2026-09-25) — une information de la fiche, telle qu'une observation d'examen la vise et la fige ;
     * {@code version} (lot C, V49) : la version de la fiche dont la valeur est lue.
     */
    public record AncrageChamp(String cle, String libelle, String valeur, Integer version) {
    }

    /**
     * ⚠️ V44 (2026-09-25, observation sur la fiche) — résout la clé {@code cle} ({@code CODE} ou {@code CODE#n}) dans le
     * référentiel de la fiche du DMC — champ actif de sa forme et de sa catégorie, rang de lot jugé comme à la saisie
     * ({@link LotsFiche}) — et lit sa valeur telle que les documents l'impriment, sur la <strong>dernière version
     * validée</strong> (celle dont le dossier porte les documents ; à défaut la dernière version). {@code valeur} nulle
     * si l'information n'est pas renseignée. 400 nominatif sur {@code nomChamp} sinon.
     *
     * <p>Sans contrôle de périmètre : l'appelant (l'examen du dossier qui porte la fiche) a déjà vérifié le sien.</p>
     */
    @Transactional(readOnly = true)
    public AncrageChamp ancrer(Long idDmc, String cleBrute, String nomChamp) {
        Contexte ctx = contexte(idDmc, false);
        FicheMarche fiche = ficheRepository.findByIdDmcOrderByNumeroVersionAsc(idDmc).stream()
                .filter(f -> StatutFicheMarche.VALIDEE.name().equals(f.getStatut())).reduce((a, b) -> b)
                .or(() -> ficheRepository.findFirstByIdDmcOrderByNumeroVersionDesc(idDmc))
                .orElseGet(() -> virtuelle(idDmc));
        FicheMarcheDto etat = toDto(ctx, fiche);
        String type = etat.getTypeMarche() != null ? etat.getTypeMarche()
                : fiche.getTypeMarche() != null ? fiche.getTypeMarche() : TypeMarcheDao.QUANTITE_FIXE.name();
        String categorie = etat.getCategorie() != null ? etat.getCategorie() : CategorieDao.FOURNITURES_SERVICES.name();
        String cle = cleBrute.trim().toUpperCase();
        int diese = cle.indexOf(LotsFiche.SEPARATEUR);
        String code = diese < 0 ? cle : cle.substring(0, diese);
        ChampFicheMarche c = champRepository.findById(code)
                .filter(x -> Boolean.TRUE.equals(x.getActif()) && x.pourTypeMarche(type) && x.pourCategorie(categorie))
                .orElseThrow(() -> new ChampsInvalidesException(List.of(new ErrorResponse.FieldError(nomChamp,
                        "« " + code + " » n'est pas une information de cette fiche (marché " + type + ", catégorie "
                                + categorie + ")."))));
        List<ErrorResponse.FieldError> erreurs = new ArrayList<>();
        String cible = LotsFiche.cleSaisie(c, cle, diese < 0 ? null : cle.substring(diese + 1),
                etat.getNbLots() == null ? 0 : etat.getNbLots(), erreurs);
        if (cible == null) {
            throw new ChampsInvalidesException(erreurs.stream()
                    .map(e -> new ErrorResponse.FieldError(nomChamp, e.message())).toList());
        }
        return new AncrageChamp(cible, c.getLibelle(), SelectionDocumentsFiche.valeurAffichee(c, cible, etat),
                fiche.getNumeroVersion());
    }

    // ------------------------------------------------------------------ lot C : versions et différences

    /** ⚠️ Lot C (2026-09-27) — la dernière version de la fiche, quel que soit son statut ({@code empty} si jamais enregistrée). */
    @Transactional(readOnly = true)
    public java.util.Optional<FicheMarche> derniereVersion(Long idDmc) {
        return ficheRepository.findFirstByIdDmcOrderByNumeroVersionDesc(idDmc);
    }

    /** ⚠️ Lot C — le numéro de la dernière version <strong>validée</strong>, {@code null} s'il n'y en a pas. */
    @Transactional(readOnly = true)
    public Integer versionValideeCourante(Long idDmc) {
        return derniereValidee(idDmc).map(FicheMarche::getNumeroVersion).orElse(null);
    }

    private java.util.Optional<FicheMarche> derniereValidee(Long idDmc) {
        return ficheRepository.findByIdDmcOrderByNumeroVersionAsc(idDmc).stream()
                .filter(f -> StatutFicheMarche.VALIDEE.name().equals(f.getStatut())).reduce((a, b) -> b);
    }

    /**
     * ⚠️ Lot C (§B4, §B5) — l'état d'une version, de quoi relire une information <strong>telle que les documents
     * l'impriment</strong> : {@link #valeur(String)} rend {@code null} si l'information n'est pas dans cette version
     * (champ inconnu, inactif, d'une autre forme ou catégorie, fermé par le cadrage, ou non renseigné).
     */
    public record EtatVersion(Integer version, FicheMarcheDto etat, Map<String, ChampFicheMarche> champs, String type,
            String categorie) {

        public String valeur(String cle) {
            if (cle == null) {
                return null;
            }
            int diese = cle.indexOf(LotsFiche.SEPARATEUR);
            ChampFicheMarche c = champs.get(diese < 0 ? cle : cle.substring(0, diese));
            Map<String, Object> cadrage = etat.getCadrage() == null ? Map.of() : etat.getCadrage();
            if (c == null || !Boolean.TRUE.equals(c.getActif()) || !c.pourTypeMarche(type) || !c.pourCategorie(categorie)
                    || !ConditionCadrage.vraie(c.getCondition(), cadrage)) {
                return null;
            }
            return SelectionDocumentsFiche.valeurAffichee(c, cle, etat);
        }

        /** Le libellé du champ de {@code cle} au référentiel, à défaut la clé elle-même. */
        public String libelle(String cle) {
            int diese = cle.indexOf(LotsFiche.SEPARATEUR);
            ChampFicheMarche c = champs.get(diese < 0 ? cle : cle.substring(0, diese));
            return c == null ? cle : c.getLibelle();
        }
    }

    /** ⚠️ Lot C — l'état de la dernière version validée ({@code empty} si aucune). Sans contrôle de périmètre. */
    @Transactional(readOnly = true)
    public java.util.Optional<EtatVersion> etatValide(Long idDmc) {
        return derniereValidee(idDmc).map(f -> etatVersion(contexte(idDmc, false), f));
    }

    private EtatVersion etatVersion(Contexte ctx, FicheMarche fiche) {
        FicheMarcheDto d = toDto(ctx, fiche);
        Map<String, ChampFicheMarche> champs = new LinkedHashMap<>();
        champRepository.findAllByOrderByCodeRubriqueAscRangAsc().forEach(c -> champs.put(c.getCode(), c));
        String type = d.getTypeMarche() != null ? d.getTypeMarche()
                : fiche.getTypeMarche() != null ? fiche.getTypeMarche() : TypeMarcheDao.QUANTITE_FIXE.name();
        String categorie = d.getCategorie() != null ? d.getCategorie() : CategorieDao.FOURNITURES_SERVICES.name();
        return new EtatVersion(fiche.getNumeroVersion(), d, champs, type, categorie);
    }

    /**
     * ⚠️ Lot C (§B5) — les informations dont la valeur imprimée diffère entre deux versions de la fiche : {@code avant}
     * nul si l'information n'est pas dans la version {@code vAvant}, {@code apres} nul si elle n'est plus dans
     * {@code vApres}. Clés dans l'ordre du référentiel puis du lot. Sans contrôle de périmètre (le dossier lu l'a fait).
     */
    @Transactional(readOnly = true)
    public List<cnm.prs.dto.PerimetreExamenDto.InformationFiche> differences(Long idDmc, Integer vAvant, Integer vApres) {
        Contexte ctx = contexte(idDmc, false);
        FicheMarche a = ficheRepository.findByIdDmcAndNumeroVersion(idDmc, vAvant).orElse(null);
        FicheMarche b = ficheRepository.findByIdDmcAndNumeroVersion(idDmc, vApres).orElse(null);
        if (a == null || b == null) {
            return List.of();
        }
        EtatVersion avant = etatVersion(ctx, a);
        EtatVersion apres = etatVersion(ctx, b);
        java.util.TreeSet<String> cles = new java.util.TreeSet<>();
        if (avant.etat().getValeurs() != null) {
            cles.addAll(avant.etat().getValeurs().keySet());
        }
        if (apres.etat().getValeurs() != null) {
            cles.addAll(apres.etat().getValeurs().keySet());
        }
        List<cnm.prs.dto.PerimetreExamenDto.InformationFiche> out = new ArrayList<>();
        for (String cle : cles) {
            String x = avant.valeur(cle);
            String y = apres.valeur(cle);
            if (!java.util.Objects.equals(x, y)) {
                out.add(new cnm.prs.dto.PerimetreExamenDto.InformationFiche(cle, LotsFiche.lotDe(cle), apres.libelle(cle), x, y));
            }
        }
        return out;
    }

    /** ⚠️ Lot 2a — un document à télécharger, au périmètre de lecture de sa fiche (404 inconnu, 403 hors périmètre). */
    @Transactional(readOnly = true)
    public DocumentFicheMarche document(Integer idDocument) {
        DocumentFicheMarche d = documentRepository.findById(idDocument)
                .orElseThrow(() -> new ResourceNotFoundException("Document introuvable : " + idDocument + "."));
        FicheMarche fiche = ficheRepository.findById(d.getIdFiche())
                .orElseThrow(() -> new ResourceNotFoundException("Document introuvable : " + idDocument + "."));
        contexte(fiche.getIdDmc());
        return d;
    }

    // ------------------------------------------------------------------ besoin (V45)

    /** ⚠️ V45 (2026-09-25, §B1) — le besoin de la version courante ; vide pour une fiche virtuelle. */
    @Transactional(readOnly = true)
    public List<cnm.prs.dto.ArticleBesoinDto> articles(Long idDmc) {
        contexte(idDmc);
        return besoin.lister(ficheRepository.findFirstByIdDmcOrderByNumeroVersionDesc(idDmc)
                .map(FicheMarche::getIdFiche).orElse(null));
    }

    /**
     * ⚠️ V45 (2026-09-25, §B1) — remplacement en bloc du besoin d'un lot ({@code lot}) ou de toute la fiche
     * ({@code lot} nul). Gardes, dans l'ordre : écriture de la fiche (profil, mandat, forme et catégorie outillées) ;
     * catégorie hors fournitures → 409 {@code BESOIN_HORS_PERIMETRE} ; rang de lot (400 {@code lot} ou
     * {@code articles[i].lot}) ; fiche validée → 409 {@code FICHE_VALIDEE} ; articles (400 nominatif).
     */
    public List<cnm.prs.dto.ArticleBesoinDto> remplacerArticles(Long idDmc, Integer lot,
            List<cnm.prs.dto.ArticleBesoinDto> articles) {
        Contexte ctx = contexteEcriture(idDmc);
        exigerBesoinDansLePerimetre(ctx);
        List<cnm.prs.dto.ArticleBesoinDto> recus = articles == null ? List.of() : articles;
        int nbLots = LotsFiche.nbLots(valeursPpm.lire(ctx.idDetail()).valeurs());
        boolean alloti = LotsFiche.alloti(nbLots);
        List<ErrorResponse.FieldError> erreurs = new ArrayList<>();
        if (lot != null && !alloti) {
            erreurs.add(new ErrorResponse.FieldError("lot", "La ligne n'est pas allotie : le besoin s'écrit sans lot."));
        } else if (lot != null && (lot < 1 || lot > nbLots)) {
            erreurs.add(new ErrorResponse.FieldError("lot", "Lot " + lot + " hors du plan : la ligne compte " + nbLots
                    + " lots (1 à " + nbLots + ")."));
        }
        for (int i = 0; erreurs.isEmpty() && i < recus.size(); i++) {
            cnm.prs.dto.ArticleBesoinDto a = recus.get(i);
            if (a == null) {
                continue;
            }
            String champ = "articles[" + i + "].lot";
            if (!alloti && a.getLot() != null) {
                erreurs.add(new ErrorResponse.FieldError(champ, "La ligne n'est pas allotie : un article n'a pas de lot."));
            } else if (alloti && lot != null && a.getLot() != null && !a.getLot().equals(lot)) {
                erreurs.add(new ErrorResponse.FieldError(champ, "Le besoin du lot " + lot + " ne reçoit pas d'article du lot "
                        + a.getLot() + "."));
            } else if (alloti && lot == null && (a.getLot() == null || a.getLot() < 1 || a.getLot() > nbLots)) {
                erreurs.add(new ErrorResponse.FieldError(champ, "La ligne compte " + nbLots + " lots : chaque article "
                        + "porte son lot (1 à " + nbLots + ")."));
            } else if (alloti && lot != null) {
                a.setLot(lot);
            }
        }
        if (!erreurs.isEmpty()) {
            throw new ChampsInvalidesException(erreurs);
        }
        FicheMarche fiche = brouillonOuNouvelle(ctx);
        boolean travaux = CategorieDao.TRAVAUX.name().equals(ctx.codeCategorie());   // ⚠️ V59 — le DQE des travaux
        BesoinFiche.valider(recus, ctx.forme().name(), travaux);
        besoin.remplacer(fiche.getIdFiche(), lot == null, lot, recus, ctx.forme().name(),
                CurrentUser.ref().or(CurrentUser::login).orElse(null),
                CurrentUser.profil().map(Enum::name).orElse(null), travaux);
        fiche.setDateMaj(LocalDateTime.now());
        ficheRepository.save(fiche);
        return besoin.lister(fiche.getIdFiche());
    }

    /** ⚠️ V45 — retire un article de la version courante (brouillon) ; 404 s'il n'en fait pas partie. */
    public void supprimerArticle(Long idDmc, Integer idArticle) {
        Contexte ctx = contexteEcriture(idDmc);
        exigerBesoinDansLePerimetre(ctx);
        FicheMarche fiche = brouillonOuNouvelle(ctx);
        if (!besoin.supprimer(fiche.getIdFiche(), idArticle)) {
            throw new ResourceNotFoundException("Article " + idArticle + " introuvable dans la fiche du DMC " + idDmc + ".");
        }
        fiche.setDateMaj(LocalDateTime.now());
        ficheRepository.save(fiche);
    }

    /**
     * Le besoin vaut pour les fournitures et services et, ⚠️ V59 (2026-10-02), pour les travaux (leur détail quantitatif
     * et estimatif) ; pas pour les prestations intellectuelles.
     */
    private static void exigerBesoinDansLePerimetre(Contexte ctx) {
        if (!besoinApplicable(ctx)) {
            throw new BusinessRuleException("Le besoin par article ne vaut que pour les fournitures et services et les travaux (catégorie "
                    + "de la fiche : " + ctx.codeCategorie() + ").", "BESOIN_HORS_PERIMETRE");
        }
    }

    // ------------------------------------------------------------------ matériel et personnel (V60)

    /** ⚠️ V60 (2026-10-03, §B1.1) — le matériel exigé de la version courante ; vide pour une fiche virtuelle. */
    @Transactional(readOnly = true)
    public List<cnm.prs.dto.MaterielExigeDto> materiel(Long idDmc) {
        contexte(idDmc);
        return moyens.materiel(idFicheCourante(idDmc));
    }

    /** ⚠️ V60 — le personnel clé exigé de la version courante ; vide pour une fiche virtuelle. */
    @Transactional(readOnly = true)
    public List<cnm.prs.dto.PersonnelExigeDto> personnel(Long idDmc) {
        contexte(idDmc);
        return moyens.personnel(idFicheCourante(idDmc));
    }

    /**
     * ⚠️ V60 (§B1.1) — remplacement de toute la liste du matériel. Gardes, dans l'ordre : écriture de la fiche ; catégorie
     * hors travaux → 409 {@code MOYENS_HORS_PERIMETRE} ; fiche validée → 409 {@code FICHE_VALIDEE} ; lignes (400
     * {@code materiel[i].…}).
     */
    public List<cnm.prs.dto.MaterielExigeDto> remplacerMateriel(Long idDmc, List<cnm.prs.dto.MaterielExigeDto> lignes) {
        Contexte ctx = contexteEcriture(idDmc);
        exigerTravaux(ctx);
        List<cnm.prs.dto.MaterielExigeDto> recues = lignes == null ? List.of() : lignes;
        FicheMarche fiche = brouillonOuNouvelle(ctx);
        MoyensFiche.validerMateriel(recues);
        moyens.remplacerMateriel(fiche.getIdFiche(), recues);
        fiche.setDateMaj(LocalDateTime.now());
        ficheRepository.save(fiche);
        return moyens.materiel(fiche.getIdFiche());
    }

    /** ⚠️ V60 — remplacement de toute la liste du personnel ; mêmes gardes (400 {@code personnel[i].…}). */
    public List<cnm.prs.dto.PersonnelExigeDto> remplacerPersonnel(Long idDmc, List<cnm.prs.dto.PersonnelExigeDto> lignes) {
        Contexte ctx = contexteEcriture(idDmc);
        exigerTravaux(ctx);
        List<cnm.prs.dto.PersonnelExigeDto> recues = lignes == null ? List.of() : lignes;
        FicheMarche fiche = brouillonOuNouvelle(ctx);
        MoyensFiche.validerPersonnel(recues);
        moyens.remplacerPersonnel(fiche.getIdFiche(), recues);
        fiche.setDateMaj(LocalDateTime.now());
        ficheRepository.save(fiche);
        return moyens.personnel(fiche.getIdFiche());
    }

    /** ⚠️ V61 (2026-10-03, §B1.1) — les pièces exigées de la version courante ; vide pour une fiche virtuelle. */
    @Transactional(readOnly = true)
    public List<cnm.prs.dto.PieceExigeeDto> pieces(Long idDmc) {
        contexte(idDmc);
        return pieces.pieces(idFicheCourante(idDmc));
    }

    /**
     * ⚠️ V61 — remplacement de toute la liste des pièces. Gardes : écriture de la fiche ; hors travaux → 409
     * {@code PIECES_HORS_PERIMETRE} ; fiche validée → 409 {@code FICHE_VALIDEE} ; lignes (400 {@code pieces[i].…}).
     */
    public List<cnm.prs.dto.PieceExigeeDto> remplacerPieces(Long idDmc, List<cnm.prs.dto.PieceExigeeDto> lignes) {
        Contexte ctx = contexteEcriture(idDmc);
        if (!CategorieDao.TRAVAUX.name().equals(ctx.codeCategorie())) {
            throw new BusinessRuleException("Les pièces de l'offre, en liste, ne valent que pour les travaux (catégorie de la "
                    + "fiche : " + ctx.codeCategorie() + ").", "PIECES_HORS_PERIMETRE");
        }
        List<cnm.prs.dto.PieceExigeeDto> recues = lignes == null ? List.of() : lignes;
        FicheMarche fiche = brouillonOuNouvelle(ctx);
        PiecesFiche.valider(recues);
        pieces.remplacer(fiche.getIdFiche(), recues);
        fiche.setDateMaj(LocalDateTime.now());
        ficheRepository.save(fiche);
        return pieces.pieces(fiche.getIdFiche());
    }

    /** ⚠️ V60 / V61 — les jetons des listes de la version (matériel, personnel, pièces), rendus dans les modèles. */
    private Map<String, String> jetonsDesListes(Integer idFiche) {
        Map<String, String> m = new LinkedHashMap<>(MoyensFiche.jetons(moyens.materiel(idFiche), moyens.personnel(idFiche)));
        m.putAll(PiecesFiche.jetons(pieces.pieces(idFiche)));
        return m;
    }

    private Integer idFicheCourante(Long idDmc) {
        return ficheRepository.findFirstByIdDmcOrderByNumeroVersionDesc(idDmc).map(FicheMarche::getIdFiche).orElse(null);
    }

    /** ⚠️ V60 — le matériel et le personnel exigés ne valent que pour les travaux. */
    private static void exigerTravaux(Contexte ctx) {
        if (!CategorieDao.TRAVAUX.name().equals(ctx.codeCategorie())) {
            throw new BusinessRuleException("Le matériel et le personnel exigés ne valent que pour les travaux (catégorie de "
                    + "la fiche : " + ctx.codeCategorie() + ").", "MOYENS_HORS_PERIMETRE");
        }
    }

    // ------------------------------------------------------------------ écritures

    public FicheMarcheDto ecrireCadrage(Long idDmc, Map<String, Object> cadrage) {
        Contexte ctx = contexteEcriture(idDmc);
        Map<String, Object> propre = validerCadrage(cadrage == null ? Map.of() : cadrage, ctx.codeCategorie(),
                ctx.forme() == null ? null : ctx.forme().name());
        FicheMarche fiche = brouillonOuNouvelle(ctx);
        fiche.setCadrage(ecrireJson(propre));
        // ⚠️ Lot 4 (2026-09-23, §B5) — reprendre le cadrage, c'est reprendre la fiche sous le type du plan : le type de
        // saisie suit, et typeChange s'éteint.
        fiche.setTypeMarche(ctx.forme().name());
        fiche.setDateMaj(LocalDateTime.now());
        fiche = ficheRepository.save(fiche);
        return toDto(ctx, fiche);
    }

    public FicheMarcheDto ecrireBloc(Long idDmc, String bloc, Map<String, Object> valeurs) {
        Contexte ctx = contexteEcriture(idDmc);
        String codeBloc = bloc == null ? "" : bloc.trim().toUpperCase();
        if (!blocRepository.existsById(codeBloc)) {
            throw new ResourceNotFoundException("Bloc introuvable : " + bloc + ".");
        }
        FicheMarche fiche = brouillonOuNouvelle(ctx);
        Map<String, Object> cadrage = lireJson(fiche.getCadrage());
        String typeMarche = ctx.forme().name();   // outillée : contexteEcriture l'a exigé
        Map<String, ChampFicheMarche> champs = new LinkedHashMap<>();
        champRepository.findAllByOrderByCodeRubriqueAscRangAsc().forEach(c -> champs.put(c.getCode(), c));
        // ⚠️ 2026-09-25 (§B2) — le nombre de lots du plan (ligne courante) : un champ « par lot » d'une ligne allotie se
        // saisit sous CODE#n, n de 1 à ce nombre.
        ValeursPpmService.ValeursPpm ppm = valeursPpm.lire(ctx.idDetail());
        int nbLots = LotsFiche.nbLots(ppm.valeurs());

        List<ErrorResponse.FieldError> erreurs = new ArrayList<>();
        Map<String, String> aEcrire = new TreeMap<>();
        for (Map.Entry<String, Object> e : (valeurs == null ? Map.<String, Object>of() : valeurs).entrySet()) {
            String cle = e.getKey() == null ? "" : e.getKey().trim().toUpperCase();
            int diese = cle.indexOf(LotsFiche.SEPARATEUR);
            String code = diese < 0 ? cle : cle.substring(0, diese);
            ChampFicheMarche c = champs.get(code);
            if (c == null || !Boolean.TRUE.equals(c.getActif())) {
                erreurs.add(new ErrorResponse.FieldError(cle, "Champ inconnu ou inactif : " + code + "."));
                continue;
            }
            if (!codeBloc.equals(c.codeBloc())) {
                erreurs.add(new ErrorResponse.FieldError(cle, "Le champ " + code + " n'appartient pas au bloc " + codeBloc + "."));
                continue;
            }
            if (!SourceChampFiche.SAISIE.name().equals(c.getSource())) {
                erreurs.add(new ErrorResponse.FieldError(cle, "« " + c.getLibelle() + " » est "
                        + (SourceChampFiche.PPM.name().equals(c.getSource()) ? "repris du PPM" : "dérivé du cadrage")
                        + " : il ne se saisit pas."));
                continue;
            }
            if (!c.pourTypeMarche(typeMarche) || !c.pourCategorie(ctx.codeCategorie()) || !ConditionCadrage.vraie(c.getCondition(), cadrage)) {
                continue;   // rubrique fermée : ignoré, pas une erreur
            }
            String brut = e.getValue() == null ? null : String.valueOf(e.getValue()).trim();
            if (brut == null || brut.isEmpty()) {
                continue;   // vide = effacé
            }
            String cible = LotsFiche.cleSaisie(c, cle, diese < 0 ? null : cle.substring(diese + 1), nbLots, erreurs);
            if (cible == null) {
                continue;
            }
            String normalisee = normaliser(c, brut, erreurs, cle);
            if (normalisee != null) {
                aEcrire.put(cible, normalisee);
            }
        }
        if (!erreurs.isEmpty()) {
            throw new ChampsInvalidesException(erreurs);
        }
        // ⚠️ V50 (2026-09-27, remise électronique, §B1.4 et Q11) — en mode électronique, le serveur POSE les valeurs
        // calculées du bloc : les cibles « si vide » quand la cellule est vide (ou renvoyée telle que calculée), la date et
        // l'heure d'ouverture des plis toujours. Les entrées se lisent sur toute la fiche (les autres blocs en base).
        Set<String> calculees = new java.util.HashSet<>();
        if (RemiseElectronique.electronique(cadrage)) {
            Map<String, String> toutes = new TreeMap<>();
            valeurRepository.findByIdFiche(fiche.getIdFiche()).stream()
                    .filter(v -> !v.getCodeChamp().startsWith(codeBloc + "-"))
                    .forEach(v -> toutes.put(v.getCodeChamp(), v.getValeur()));
            toutes.putAll(aEcrire);
            List<ChampFicheMarche> ouverts = champs.values().stream()
                    .filter(c -> Boolean.TRUE.equals(c.getActif()) && c.pourTypeMarche(typeMarche)
                            && c.pourCategorie(ctx.codeCategorie()) && ConditionCadrage.vraie(c.getCondition(), cadrage))
                    .toList();
            for (Map.Entry<String, String> calc : RemiseElectronique.calculs(ouverts, toutes, ppm.dates()).entrySet()) {
                String cible = calc.getKey();
                if (!cible.startsWith(codeBloc + "-")) {
                    continue;
                }
                String recue = aEcrire.get(cible);
                if (RemiseElectronique.TOUJOURS_CALCULES.contains(cible) || recue == null || recue.equals(calc.getValue())) {
                    aEcrire.put(cible, calc.getValue());
                    calculees.add(cible);
                }
            }
        }
        // ⚠️ 2026-09-29 (demande front « champs non imprimés », §B1.2) — le bloc est remplacé, SAUF les valeurs des champs
        // retirés (inactifs) : l'écran ne les renvoie plus, et un retrait ne perd rien de ce qui a été saisi.
        for (FicheMarcheValeur v : valeurRepository.findByIdFiche(fiche.getIdFiche())) {
            String cle = v.getCodeChamp();
            if (!cle.startsWith(codeBloc + "-")) {
                continue;
            }
            int d = cle.indexOf(LotsFiche.SEPARATEUR);
            ChampFicheMarche c = champs.get(d < 0 ? cle : cle.substring(0, d));
            if (c == null || Boolean.TRUE.equals(c.getActif())) {
                valeurRepository.delete(v);
            }
        }
        valeurRepository.flush();
        for (Map.Entry<String, String> e : aEcrire.entrySet()) {
            valeurRepository.save(new FicheMarcheValeur(null, fiche.getIdFiche(), e.getKey(), e.getValue(),
                    calculees.contains(e.getKey())));
        }
        fiche.setDateMaj(LocalDateTime.now());
        fiche = ficheRepository.save(fiche);
        return toDto(ctx, fiche);
    }

    // ------------------------------------------------------------------ import du DAO (2026-09-28, ADR-0012)

    /**
     * ⚠️ Import du DAO (demande front du 2026-09-28, §B1) — l'état d'une fiche où l'on peut importer, gardes comprises :
     * PRMP propriétaire ou son UGPM seulement (403, l'Administrateur n'importe pas), DMC inconnu → 404, hors périmètre →
     * 403, pas un DAO → 409 {@code DMC_NON_DAO}, mandat inactif → 409, forme ou catégorie non outillée → 409
     * {@code FORME_NON_OUTILLEE}, dernière version validée → 409 {@code FICHE_VALIDEE}. Une fiche jamais enregistrée est
     * servie virtuelle (brouillon vide) : rien n'est écrit.
     */
    @Transactional(readOnly = true)
    public FicheMarcheDto etatImportable(Long idDmc) {
        Contexte ctx = contexteImport(idDmc);
        FicheMarche fiche = ficheRepository.findFirstByIdDmcOrderByNumeroVersionDesc(idDmc).orElseGet(() -> virtuelle(idDmc));
        return toDto(ctx, fiche);
    }

    /** Les réponses de cadrage validées comme par {@code PUT …/cadrage} ; {@link ChampsInvalidesException} sinon. */
    @Transactional(readOnly = true)
    public Map<String, Object> cadrageValide(Map<String, Object> reponses, String categorie, String typeMarche) {
        return validerCadrage(reponses, categorie, typeMarche);
    }

    private Contexte contexteImport(Long idDmc) {
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        if (profil != ProfilUtilisateur.PRMP && profil != ProfilUtilisateur.UGPM) {
            throw new AccessDeniedException("L'import du DAO se fait par la PRMP propriétaire ou son UGPM.");
        }
        Contexte ctx = contexte(idDmc);
        dossierIntegrite.exigerMandatActif();
        exigerFormeOutillee(ctx);
        FicheMarche derniere = ficheRepository.findFirstByIdDmcOrderByNumeroVersionDesc(idDmc).orElse(null);
        if (derniere != null && StatutFicheMarche.VALIDEE.name().equals(derniere.getStatut())) {
            throw new BusinessRuleException("La version " + derniere.getNumeroVersion() + " est validée, donc figée : "
                    + "ouvrez une révision pour y importer un DAO.", "FICHE_VALIDEE");
        }
        return ctx;
    }

    /**
     * ⚠️ Import du DAO (demande front du 2026-09-28, §B2) — écrit, <strong>d'un seul coup</strong>, ce que la PRMP a retenu
     * de la lecture : le cadrage reçoit les clés envoyées (les autres restent), les valeurs s'écrivent champ par champ, tous
     * blocs confondus, et un code absent n'est pas effacé. Tout est validé d'abord — même {@link #normaliser}, mêmes clés de
     * cadrage que {@code PUT …/cadrage}, mêmes conditions d'affichage évaluées sur le cadrage fusionné — et un refus rend la
     * liste nominative (400) sans rien écrire. À la différence d'un bloc enregistré, un champ fermé n'est pas ignoré en
     * silence : la PRMP l'a coché, le refus le lui dit. Journal du dossier de planification : {@code FICHE_IMPORTEE}.
     */
    public FicheMarcheDto appliquerImport(Long idDmc, Map<String, Object> cadrageRecu, Map<String, Object> valeursRecues,
            String fichier, String empreinte) {
        Contexte ctx = contexteImport(idDmc);
        String typeMarche = ctx.forme().name();
        List<ErrorResponse.FieldError> erreurs = new ArrayList<>();
        if (fichier == null || fichier.isBlank()) {
            erreurs.add(new ErrorResponse.FieldError("fichier", "Le nom du fichier importé est exigé (il va au journal)."));
        }
        String emp = empreinte == null ? "" : empreinte.trim().toLowerCase(java.util.Locale.ROOT);
        if (!emp.matches("[0-9a-f]{64}")) {
            erreurs.add(new ErrorResponse.FieldError("empreinte",
                    "L'empreinte SHA-256 du fichier importé est exigée (64 caractères hexadécimaux, celle de la lecture)."));
        }
        Map<String, Object> cadrageEnvoye = cadrageRecu == null ? Map.of() : cadrageRecu;
        Map<String, Object> valeursEnvoyees = valeursRecues == null ? Map.of() : valeursRecues;
        if (cadrageEnvoye.isEmpty() && valeursEnvoyees.isEmpty()) {
            erreurs.add(new ErrorResponse.FieldError("valeurs", "Rien à appliquer : aucune valeur ni réponse de cadrage retenue."));
        }

        // Le cadrage : seules les clés envoyées sont validées (celles de la fiche l'ont été à leur écriture), puis fusionnées.
        FicheMarche existante = ficheRepository.findFirstByIdDmcOrderByNumeroVersionDesc(idDmc).orElse(null);
        Map<String, Object> fusion = existante == null ? new LinkedHashMap<>() : new LinkedHashMap<>(lireJson(existante.getCadrage()));
        Map<String, Object> aValider = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : cadrageEnvoye.entrySet()) {
            if (e.getValue() == null || String.valueOf(e.getValue()).isBlank()) {
                erreurs.add(new ErrorResponse.FieldError(String.valueOf(e.getKey()), "Réponse vide : l'import n'efface rien."));
            } else {
                aValider.put(e.getKey(), e.getValue());
            }
        }
        Map<String, Object> cadrageValide = Map.of();
        try {
            cadrageValide = validerCadrage(aValider, ctx.codeCategorie(), typeMarche);
        } catch (ChampsInvalidesException ex) {
            erreurs.addAll(ex.getErreurs());
        }
        fusion.putAll(cadrageValide);

        // Les valeurs : mêmes refus que la saisie d'un bloc, le bloc en moins.
        Map<String, ChampFicheMarche> champs = new LinkedHashMap<>();
        champRepository.findAllByOrderByCodeRubriqueAscRangAsc().forEach(c -> champs.put(c.getCode(), c));
        ValeursPpmService.ValeursPpm ppm = valeursPpm.lire(ctx.idDetail());
        int nbLots = LotsFiche.nbLots(ppm.valeurs());
        Map<String, String> aEcrire = new TreeMap<>();
        for (Map.Entry<String, Object> e : valeursEnvoyees.entrySet()) {
            String cle = e.getKey() == null ? "" : e.getKey().trim().toUpperCase();
            int diese = cle.indexOf(LotsFiche.SEPARATEUR);
            String code = diese < 0 ? cle : cle.substring(0, diese);
            ChampFicheMarche c = champs.get(code);
            if (c == null || !Boolean.TRUE.equals(c.getActif())) {
                erreurs.add(new ErrorResponse.FieldError(cle, "Champ inconnu ou inactif : " + code + "."));
                continue;
            }
            if (!SourceChampFiche.SAISIE.name().equals(c.getSource())) {
                erreurs.add(new ErrorResponse.FieldError(cle, "« " + c.getLibelle() + " » est "
                        + (SourceChampFiche.PPM.name().equals(c.getSource()) ? "repris du PPM (le plan fait foi)" : "dérivé du cadrage")
                        + " : il ne s'importe pas."));
                continue;
            }
            if (!c.pourTypeMarche(typeMarche) || !c.pourCategorie(ctx.codeCategorie())) {
                erreurs.add(new ErrorResponse.FieldError(cle, "« " + c.getLibelle() + " » ne vaut pas pour cette forme de marché."));
                continue;
            }
            if (!ConditionCadrage.vraie(c.getCondition(), fusion)) {
                erreurs.add(new ErrorResponse.FieldError(cle, "« " + c.getLibelle() + " » ne s'applique pas à cette fiche "
                        + "(condition : " + c.getCondition() + ")."));
                continue;
            }
            String brut = e.getValue() == null ? null : String.valueOf(e.getValue()).trim();
            if (brut == null || brut.isEmpty()) {
                erreurs.add(new ErrorResponse.FieldError(cle, "Valeur vide : l'import n'efface rien."));
                continue;
            }
            String cible = LotsFiche.cleSaisie(c, cle, diese < 0 ? null : cle.substring(diese + 1), nbLots, erreurs);
            if (cible == null) {
                continue;
            }
            String normalisee = normaliser(c, brut, erreurs, cle);
            if (normalisee != null) {
                aEcrire.put(cible, normalisee);
            }
        }
        if (!erreurs.isEmpty()) {
            throw new ChampsInvalidesException(erreurs);
        }

        // Écriture : rien n'a été refusé.
        FicheMarche fiche = brouillonOuNouvelle(ctx);
        if (!cadrageValide.isEmpty()) {
            Map<String, Object> cadrageFiche = new LinkedHashMap<>(lireJson(fiche.getCadrage()));
            cadrageFiche.putAll(cadrageValide);
            fiche.setCadrage(ecrireJson(cadrageFiche));
            fiche.setTypeMarche(typeMarche);   // comme PUT …/cadrage : la fiche est reprise sous le type du plan
        }
        Map<String, FicheMarcheValeur> enBase = new LinkedHashMap<>();
        valeurRepository.findByIdFiche(fiche.getIdFiche()).forEach(v -> enBase.put(v.getCodeChamp(), v));
        for (Map.Entry<String, String> e : aEcrire.entrySet()) {
            FicheMarcheValeur v = enBase.get(e.getKey());
            if (v == null) {
                v = new FicheMarcheValeur(null, fiche.getIdFiche(), e.getKey(), e.getValue());
            } else {
                v.setValeur(e.getValue());
                v.setCalculee(Boolean.FALSE);
            }
            enBase.put(e.getKey(), valeurRepository.save(v));
        }
        // ⚠️ V50 (remise électronique) — comme à l'enregistrement d'un bloc : les cibles calculées se reposent, les
        // « toujours calculées » toujours, les « si vide » quand elles sont vides ou encore calculées et non importées.
        Map<String, Object> cadrageFinal = lireJson(fiche.getCadrage());
        if (RemiseElectronique.electronique(cadrageFinal)) {
            Map<String, String> toutes = new TreeMap<>();
            enBase.forEach((k, v) -> toutes.put(k, v.getValeur()));
            List<ChampFicheMarche> ouverts = champs.values().stream()
                    .filter(c -> Boolean.TRUE.equals(c.getActif()) && c.pourTypeMarche(typeMarche)
                            && c.pourCategorie(ctx.codeCategorie()) && ConditionCadrage.vraie(c.getCondition(), cadrageFinal))
                    .toList();
            for (Map.Entry<String, String> calc : RemiseElectronique.calculs(ouverts, toutes, ppm.dates()).entrySet()) {
                FicheMarcheValeur v = enBase.get(calc.getKey());
                boolean poser = RemiseElectronique.TOUJOURS_CALCULES.contains(calc.getKey()) || v == null
                        || Boolean.TRUE.equals(v.getCalculee()) && !aEcrire.containsKey(calc.getKey());
                if (!poser) {
                    continue;
                }
                if (v == null) {
                    v = new FicheMarcheValeur(null, fiche.getIdFiche(), calc.getKey(), calc.getValue(), true);
                } else {
                    v.setValeur(calc.getValue());
                    v.setCalculee(Boolean.TRUE);
                }
                enBase.put(calc.getKey(), valeurRepository.save(v));
            }
        }
        fiche.setDateMaj(LocalDateTime.now());
        fiche = ficheRepository.save(fiche);
        journal.tracer(ctx.idDossier(), JournalDossierService.FICHE_IMPORTEE, "fiche pré-remplie par import de "
                + fichier.trim() + " (" + emp.substring(0, 12) + ") : " + aEcrire.size() + " valeurs, "
                + cadrageValide.size() + " réponses de cadrage");
        return toDto(ctx, fiche);
    }

    /**
     * La PRMP <strong>seule</strong> valide (403 pour l'UGPM et l'Administrateur : c'est l'acte qui engage). 409
     * {@code CONTROLES_BLOQUANTS} si le bilan en porte ; {@code FICHE_VIDE} sans brouillon ; {@code FICHE_VALIDEE} si la
     * dernière version l'est déjà. Journal du dossier : {@code FICHE_MARCHE_VALIDEE}.
     */
    public FicheMarcheDto valider(Long idDmc) {
        Contexte ctx = contexte(idDmc);
        if (CurrentUser.profil().orElse(null) != ProfilUtilisateur.PRMP) {
            throw new AccessDeniedException("Seule la PRMP valide la fiche marché.");
        }
        dossierIntegrite.exigerMandatActif();
        exigerFormeOutillee(ctx);
        FicheMarche fiche = ficheRepository.findFirstByIdDmcOrderByNumeroVersionDesc(idDmc)
                .orElseThrow(() -> new BusinessRuleException("La fiche n'a jamais été enregistrée : rien à valider.", "FICHE_VIDE"));
        if (StatutFicheMarche.VALIDEE.name().equals(fiche.getStatut())) {
            throw new BusinessRuleException("La version " + fiche.getNumeroVersion() + " est déjà validée : "
                    + "ouvrez une révision pour la modifier.", "FICHE_VALIDEE");
        }
        FicheMarcheDto etat = toDto(ctx, fiche);
        BilanControlesDto bilan = etat.getBilanControles();
        if (!bilan.bloquants().isEmpty()) {
            throw new BusinessRuleException(bilan.bloquants().size() + " contrôle(s) bloquant(s) : "
                    + bilan.bloquants().get(0).message(), "CONTROLES_BLOQUANTS");
        }
        // ⚠️ Lot 2a (2026-09-23, §B1) — les documents de la version sont produits AVANT qu'elle ne soit figée : un échec
        // (GenerationDocumentsException, 500 nommé) laisse la fiche en brouillon et annule la transaction.
        LocalDateTime maintenant = LocalDateTime.now();
        List<DocumentsFicheMarcheService.Produit> produits = documents.produire(etat,
                besoinApplicable(ctx) ? besoin.articles(fiche.getIdFiche()) : List.of(), maintenant,
                // ⚠️ V60 (§B2) — les jetons {{MOYENS.materiel}} et {{MOYENS.personnel}} ; ⚠️ V61 — {{PIECES.*}}
                jetonsDesListes(fiche.getIdFiche()));
        fiche.setStatut(StatutFicheMarche.VALIDEE.name());
        fiche.setDateValidation(maintenant);
        fiche.setValidePar(CurrentUser.ref().orElse(null));
        fiche = ficheRepository.save(fiche);
        documents.enregistrer(fiche.getIdFiche(), produits, maintenant);
        journal.tracer(ctx.idDossier(), JournalDossierService.FICHE_MARCHE_VALIDEE,
                "DAO, version " + fiche.getNumeroVersion() + ", " + bilan.nbSaisis() + " information(s)");
        // Le dossier soumis que porte déjà la fiche reçoit les documents de cette version (lot 2a, §B3/§B4).
        Integer idDossierSoumis = dossierRepository.findIdDossierByIdDmc(idDmc).orElse(null);
        if (idDossierSoumis != null) {
            int remplacees = documents.joindre(idDossierSoumis, idDmc);
            // ⚠️ Lot C (2026-09-27, §B2) — la révision validée d'une fiche dont le dossier existe : le journal du dossier
            // soumis dit la version, ce qui a changé et ce qui a été remplacé.
            Integer n = fiche.getNumeroVersion();
            FicheMarche precedente = n == null ? null : ficheRepository.findByIdDmcOrderByNumeroVersionAsc(idDmc).stream()
                    .filter(f -> StatutFicheMarche.VALIDEE.name().equals(f.getStatut()) && f.getNumeroVersion() != null
                            && f.getNumeroVersion() < n)
                    .reduce((a, b) -> b).orElse(null);
            if (precedente != null) {
                int modifiees = differences(idDmc, precedente.getNumeroVersion(), n).size();
                journal.tracer(idDossierSoumis, JournalDossierService.FICHE_REVISEE, "Fiche marché version " + n
                        + " validée, " + modifiees + " information(s) modifiée(s), " + remplacees + " pièce(s) remplacée(s)");
            }
        }
        return toDto(ctx, fiche);
    }

    /**
     * ⚠️ 2026-09-26 (demande front du 2026-09-25, « défaire une fiche marché ouverte par erreur », §B1) — supprime une
     * fiche <strong>sans historique</strong> : son DMC, ses versions (brouillon), ses valeurs et son besoin ; la ligne
     * du plan redevient préparable. Refus, dans l'ordre : profil autre que PRMP / UGPM → 403 ; DMC inconnu → 404, hors
     * périmètre → 403, pas un DAO → 409 {@code DMC_NON_DAO} ; mandat inactif → 409 ; dernière version validée → 409
     * {@code FICHE_VALIDEE} ; une version validée dans l'historique (révision ouverte) → 409
     * {@code FICHE_AVEC_HISTORIQUE} ; un document produit → 409 {@code FICHE_AVEC_DOCUMENTS} ; rattachée à un dossier
     * → 409 {@code FICHE_AVEC_DOSSIER} avec {@code idDossier}. La forme et la catégorie de la ligne n'importent pas :
     * une fiche devenue non outillée se supprime aussi. Journal du plan : {@code FICHE_MARCHE_SUPPRIMEE}. Une fiche qui a
     * de l'histoire ne se supprime ni ne s'abandonne : la révision la corrige (§B2, arbitré par le pilote le 26/09).
     */
    public void supprimer(Long idDmc) {
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        if (profil != ProfilUtilisateur.PRMP && profil != ProfilUtilisateur.UGPM) {
            throw new AccessDeniedException("Une fiche marché se supprime par la PRMP propriétaire ou son UGPM.");
        }
        Contexte ctx = contexte(idDmc);
        dossierIntegrite.exigerMandatActif();
        List<FicheMarche> versions = ficheRepository.findByIdDmcOrderByNumeroVersionAsc(idDmc);
        FicheMarche derniere = versions.isEmpty() ? null : versions.get(versions.size() - 1);
        if (derniere != null && StatutFicheMarche.VALIDEE.name().equals(derniere.getStatut())) {
            throw new BusinessRuleException("La version " + derniere.getNumeroVersion() + " de cette fiche est validée : "
                    + "elle ne se supprime pas (la révision est le geste prévu).", "FICHE_VALIDEE");
        }
        if (versions.stream().anyMatch(f -> StatutFicheMarche.VALIDEE.name().equals(f.getStatut()))) {
            throw new BusinessRuleException("Cette fiche a déjà une version validée : son historique se conserve, elle ne "
                    + "se supprime pas.", "FICHE_AVEC_HISTORIQUE");
        }
        if (versions.stream().anyMatch(f -> !documentRepository.findByIdFicheOrderByIdDocumentAsc(f.getIdFiche()).isEmpty())) {
            throw new BusinessRuleException("Cette fiche a déjà produit des documents : elle ne se supprime plus.",
                    "FICHE_AVEC_DOCUMENTS");
        }
        Integer idDossierSoumis = dossierRepository.findIdDossierByIdDmc(idDmc).orElse(null);
        if (idDossierSoumis != null) {
            throw new BusinessRuleException("Cette fiche est rattachée au dossier " + idDossierSoumis + " : détachez-la "
                    + "d'abord.", "FICHE_AVEC_DOSSIER", idDossierSoumis);
        }
        for (FicheMarche f : versions) {
            valeurRepository.deleteAll(valeurRepository.findByIdFiche(f.getIdFiche()));
        }
        ficheRepository.deleteAll(versions);   // le besoin suit (ON DELETE CASCADE, V45)
        ficheRepository.flush();
        dmcRepository.delete(ctx.dmc());
        // ⚠️ 2026-09-27 (règle du pilote, demande front « statut Lancé », §B2) — le geste qui referme la mise en
        // concurrence rend « Prévu » à la ligne « Lancé » (toute sa filiation : la copie d'une version en cours aussi) ;
        // un statut manuel (CHDP, DSS) reste tel quel.
        List<Integer> renduesPrevues = new ArrayList<>();
        Marche ligneDmc = marcheRepository.findById(ctx.idDetail()).orElse(null);
        for (Marche m : ligneDmc == null ? List.<Marche>of() : marcheRepository.findFiliation(ligneDmc.getIdLigneOrigine())) {
            if (m.getStatut() != null && StatutMarcheService.CODE_LANCE.equalsIgnoreCase(m.getStatut().trim())) {
                m.setStatut(StatutMarcheService.CODE_DEFAUT);
                marcheRepository.save(m);
                renduesPrevues.add(m.getIdDetail());
            }
        }
        journal.tracer(ctx.idDossier(), JournalDossierService.FICHE_MARCHE_SUPPRIMEE,
                "DAO de la ligne " + ctx.idDetail() + " (DMC " + idDmc + ") supprimé, sans historique"
                        + (renduesPrevues.isEmpty() ? "" : " ; statut rendu à " + StatutMarcheService.CODE_DEFAUT
                                + " (ligne" + (renduesPrevues.size() > 1 ? "s " : " ")
                                + renduesPrevues.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", ")) + ")"));
    }

    /**
     * ⚠️ Lot C (2026-09-27, §B1, Q3 arbitrée par le pilote) — les statuts où la Commission <strong>tient</strong> la
     * version examinée : la fiche du dossier ne se révise pas. Elle se révise quand le dossier revient à la PRMP
     * ({@code EN_ATTENTE_DECISION_PRMP}, {@code EN_ATTENTE_PIECES}, {@code EN_ATTENTE_COMPLEMENTS_DEPOT}) ou n'est pas
     * encore parti ({@code BROUILLON}).
     */
    static final Set<String> STATUTS_TENUS_PAR_LA_COMMISSION = Set.of(cnm.prs.enums.StatutDossier.SOUMIS.name(),
            cnm.prs.enums.StatutDossier.PRET_DISPATCH.name(), cnm.prs.enums.StatutDossier.DISPATCHE.name(),
            cnm.prs.enums.StatutDossier.EXAMINE.name(), cnm.prs.enums.StatutDossier.A_REEXAMINER.name(),
            cnm.prs.enums.StatutDossier.PV_SIGNE.name(), cnm.prs.enums.StatutDossier.EN_VERIFICATION.name(),
            cnm.prs.enums.StatutDossier.OBSERVATIONS_LEVEES.name(), cnm.prs.enums.StatutDossier.DECISION_TRANSMISE_SIGMP.name(),
            cnm.prs.enums.StatutDossier.CLOTURE.name());

    /**
     * Ouvre la version suivante en brouillon, copie de la dernière validée. ⚠️ Lot C : 409 {@code DOSSIER_EN_EXAMEN}
     * ({@code idDossier}, {@code details.statut}) tant que le dossier soumis est entre les mains de la Commission.
     */
    public FicheMarcheDto reviser(Long idDmc) {
        Contexte ctx = contexteEcriture(idDmc);
        dossierRepository.findIdDossierByIdDmc(idDmc).flatMap(dossierRepository::findById).ifPresent(d -> {
            if (STATUTS_TENUS_PAR_LA_COMMISSION.contains(d.getStatut())) {
                throw new BusinessRuleException("Le dossier " + d.getIdDossier() + " est entre les mains de la Commission "
                        + "(statut « " + d.getStatut() + " ») : la fiche marché se révise quand il revient à la PRMP "
                        + "(observations maintenues ou lettre de renvoi).", "DOSSIER_EN_EXAMEN", d.getIdDossier(),
                        Map.of("statut", d.getStatut()));
            }
        });
        FicheMarche derniere = ficheRepository.findFirstByIdDmcOrderByNumeroVersionDesc(idDmc)
                .orElseThrow(() -> new BusinessRuleException("La fiche n'a jamais été enregistrée : rien à réviser.", "FICHE_VIDE"));
        if (!StatutFicheMarche.VALIDEE.name().equals(derniere.getStatut())) {
            throw new BusinessRuleException("La version " + derniere.getNumeroVersion() + " est encore en brouillon.",
                    "BROUILLON_EN_COURS");
        }
        FicheMarche suivante = new FicheMarche();
        suivante.setIdDmc(idDmc);
        suivante.setNumeroVersion(derniere.getNumeroVersion() + 1);
        suivante.setStatut(StatutFicheMarche.BROUILLON.name());
        suivante.setTypeMarche(ctx.forme().name());   // lot 1c : le type dérivé au moment de la révision
        suivante.setCadrage(derniere.getCadrage());
        suivante.setDateCreation(LocalDateTime.now());
        suivante.setCreePar(CurrentUser.ref().orElse(null));
        suivante = ficheRepository.save(suivante);
        // ⚠️ 2026-09-25 (arbitrage du pilote, formulaires du candidat §B2) — une version validée n'est jamais convertie ;
        // la révision ne reprend que les valeurs que le référentiel d'aujourd'hui admet encore (voir valeurReprise) : les
        // autres restent lisibles dans la version précédente et se ressaisissent.
        Map<String, ChampFicheMarche> referentiel = new LinkedHashMap<>();
        champRepository.findAllByOrderByCodeRubriqueAscRangAsc().forEach(c -> referentiel.put(c.getCode(), c));
        int nbLots = LotsFiche.nbLots(valeursPpm.lire(ctx.idDetail()).valeurs());
        for (FicheMarcheValeur v : valeurRepository.findByIdFiche(derniere.getIdFiche())) {
            if (valeurReprise(referentiel, v.getCodeChamp(), v.getValeur(), nbLots)) {
                valeurRepository.save(new FicheMarcheValeur(null, suivante.getIdFiche(), v.getCodeChamp(), v.getValeur(),
                        Boolean.TRUE.equals(v.getCalculee())));   // V50 : la mention « calculée » suit la valeur
            }
        }
        besoin.copier(derniere.getIdFiche(), suivante.getIdFiche());   // ⚠️ V45 — le besoin suit, comme les valeurs
        moyens.copier(derniere.getIdFiche(), suivante.getIdFiche());   // ⚠️ V60 — le matériel et le personnel aussi
        pieces.copier(derniere.getIdFiche(), suivante.getIdFiche());   // ⚠️ V61 — les pièces de l'offre aussi
        return toDto(ctx, suivante);
    }

    /**
     * ⚠️ 2026-09-25 — une valeur passe à la révision si le référentiel l'admet encore : champ connu et actif (sinon valeur
     * orpheline : {@code B02-AU-03}), clé de la forme que le champ attend sur cette ligne (une clé nue sur un champ devenu
     * par lot d'une ligne allotie ne se répartit pas toute seule : {@code B09-LL-01}), valeur parmi les options d'une liste
     * (un texte libre dans une liste serait un contresens : {@code B04-CD-01}, {@code B04-CD-02}). Aucune conversion.
     */
    static boolean valeurReprise(Map<String, ChampFicheMarche> referentiel, String cle, String valeur, int nbLots) {
        int diese = cle.indexOf(LotsFiche.SEPARATEUR);
        ChampFicheMarche c = referentiel.get(diese < 0 ? cle : cle.substring(0, diese));
        if (c == null || !Boolean.TRUE.equals(c.getActif())) {
            return false;
        }
        if ((diese >= 0) != LotsFiche.parLot(c, nbLots)) {
            return false;
        }
        List<String> options = ChampFicheMarche.options(c.getOptions());
        if (TypeChampFiche.LISTE.name().equals(c.getType()) && !options.isEmpty()) {
            return valeur != null && options.stream().anyMatch(o -> o.equalsIgnoreCase(valeur.trim()));
        }
        if (TypeChampFiche.LISTE_MULTIPLE.name().equals(c.getType())) {
            List<String> choix = ChampFicheMarche.liste(valeur);
            return !choix.isEmpty() && choix.stream().allMatch(x -> options.stream().anyMatch(o -> o.equalsIgnoreCase(x)));
        }
        return true;
    }

    // ------------------------------------------------------------------ contexte et gardes

    /**
     * Le DMC, sa ligne et son dossier — 404 si absent, 403 hors périmètre, 409 si le DMC n'est pas un DAO. ⚠️ Lot 1c :
     * {@code forme} = la forme saisie de la <strong>ligne courante</strong> de la filiation (même lecture que
     * {@code valeursPpm}), d'où se déduit le type de marché ; {@code null} si le plan ne la porte pas.
     */
    private record Contexte(DossierMec dmc, Integer idDetail, Integer idDossier, FormeMarche forme,
            DmcService.CategorieLigne categorie) {

        /** ⚠️ Lot 5 — code de la catégorie ({@code null} si le plan ou le référentiel ne la donne pas). */
        String codeCategorie() {
            return categorie.categorie() == null ? null : categorie.categorie().name();
        }

        /** La forme et la catégorie sont outillées. */
        boolean outille() {
            return DmcService.motifForme(forme).isEmpty() && categorie.motif().isEmpty();
        }
    }

    private Contexte contexte(Long idDmc) {
        return contexte(idDmc, true);
    }

    /** {@code controlerPerimetre = false} : l'appelant a déjà vérifié un périmètre qui l'englobe (lot 1b). */
    private Contexte contexte(Long idDmc, boolean controlerPerimetre) {
        DossierMec dmc = dmcRepository.findById(idDmc)
                .orElseThrow(() -> new ResourceNotFoundException("DMC introuvable : " + idDmc));
        Integer idDossier = marcheRepository.findIdDossierByIdDetail(dmc.getIdDetail()).orElse(null);
        if (controlerPerimetre) {
            perimetre.controler(idDossier);
        }
        TypeDmc type = typeDmcRepository.findById(dmc.getIdTypeDmc()).orElse(null);
        if (type == null || !"DAO".equalsIgnoreCase(type.getCode())) {
            throw new BusinessRuleException("Le DMC " + idDmc + " n'est pas un dossier d'appel d'offres ("
                    + (type == null ? "type inconnu" : type.getCode()) + ") : pas de fiche marché.", "DMC_NON_DAO");
        }
        Marche ligne = marcheRepository.findById(dmc.getIdDetail()).map(valeursPpm::ligneEnVigueur).orElse(null);
        FormeMarche forme = ligne == null ? null : ligne.formeMarcheSaisie();
        // ⚠️ Lot 5 (2026-09-24) — la catégorie se lit sur la nature de la même ligne courante, comme la forme.
        return new Contexte(dmc, dmc.getIdDetail(), idDossier, forme, dmcService.categorie(ligne));
    }

    private Contexte contexteEcriture(Long idDmc) {
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        if (profil != ProfilUtilisateur.PRMP && profil != ProfilUtilisateur.UGPM
                && profil != ProfilUtilisateur.ADMINISTRATEUR) {
            throw new AccessDeniedException("La fiche marché s'écrit par la PRMP, son UGPM ou l'Administrateur.");
        }
        Contexte ctx = contexte(idDmc);
        dossierIntegrite.exigerMandatActif();
        exigerFormeOutillee(ctx);
        return ctx;
    }

    /**
     * ⚠️ Lot 1c (2026-09-23, §B2) — une fiche ne s'écrit, ne se valide ni ne se révise que si la forme du marché de sa
     * ligne courante est outillée : 409 {@code FORME_NON_OUTILLEE} (forme absente du plan, ou contrat-cadre / à commande).
     * La lecture reste ouverte.
     */
    private static void exigerFormeOutillee(Contexte ctx) {
        DmcService.motifForme(ctx.forme()).ifPresent(m -> {
            throw new BusinessRuleException(m.message(), m.code());
        });
        // ⚠️ Lot 5 (2026-09-24) — même refus, même code, pour une catégorie non outillée ou absente.
        ctx.categorie().motif().ifPresent(m -> {
            throw new BusinessRuleException(m.message(), m.code());
        });
    }

    private FicheMarche brouillonOuNouvelle(Contexte ctx) {
        Long idDmc = ctx.dmc().getIdDmc();
        FicheMarche derniere = ficheRepository.findFirstByIdDmcOrderByNumeroVersionDesc(idDmc).orElse(null);
        if (derniere == null) {
            FicheMarche f = virtuelle(idDmc);
            f.setTypeMarche(ctx.forme().name());   // le type sous lequel la fiche est saisie (lot 1c : typeChange)
            f.setCreePar(CurrentUser.ref().orElse(null));
            f = ficheRepository.save(f);
            // ⚠️ V47 (2026-09-26, formulaires du candidat, R6) — les valeurs par défaut du référentiel sont RECOPIÉES à la
            // création de la fiche (champs saisis, actifs, de sa forme et de sa catégorie) : la fiche les porte ensuite comme
            // toute saisie, et un changement de défaut ne la touche plus.
            for (ChampFicheMarche c : champRepository.findByActifTrueOrderByCodeRubriqueAscRangAsc()) {
                if (c.getValeurDefaut() != null && SourceChampFiche.SAISIE.name().equals(c.getSource())
                        && c.pourTypeMarche(ctx.forme().name())
                        && c.pourCategorie(ctx.codeCategorie() != null ? ctx.codeCategorie() : CategorieDao.FOURNITURES_SERVICES.name())) {
                    // ⚠️ V50 (2026-09-27, §B1.4) — un défaut « PARAM:<CLE> » se lit dans le paramètre du moment (rien s'il est vide).
                    // ⚠️ 2026-09-28 (contrat-cadre, §B7) — « MANDAT:ACTE_NOMINATION » se lit dans le mandat en vigueur.
                    String defaut = DEFAUT_ACTE_NOMINATION.equals(c.getValeurDefaut()) ? acteDeNomination(ctx)
                            : parametres.valeurDefaut(c.getValeurDefaut());
                    if (defaut != null && !defaut.isBlank()) {
                        valeurRepository.save(new FicheMarcheValeur(null, f.getIdFiche(), c.getCode(), defaut));
                    }
                }
            }
            return f;
        }
        if (StatutFicheMarche.VALIDEE.name().equals(derniere.getStatut())) {
            throw new BusinessRuleException("La version " + derniere.getNumeroVersion() + " est validée, donc figée : "
                    + "ouvrez une révision (POST /reviser) pour la modifier.", "FICHE_VALIDEE");
        }
        return derniere;
    }

    /**
     * ⚠️ 2026-09-28 (demande front « contrat-cadre aligné sur le modèle officiel », §B7) — la valeur par défaut qui se lit
     * dans le <strong>mandat PRMP en vigueur</strong> : « acte de nomination de la PRMP (nature, numéro, date) ».
     */
    static final String DEFAUT_ACTE_NOMINATION = "MANDAT:ACTE_NOMINATION";

    /**
     * L'acte de nomination de la PRMP qui prépare la fiche, lu dans son mandat déclaré en vigueur aujourd'hui :
     * « référence de l'arrêté du JJ/MM/AAAA ». La PRMP est l'utilisateur courant (PRMP, ou la tutelle d'une UGPM), à défaut
     * (Administrateur) celle du plan. {@code null} sans mandat déclaré (mandat implicite) : le champ reste à saisir.
     */
    private String acteDeNomination(Contexte ctx) {
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        String idPrmp = profil == ProfilUtilisateur.PRMP || profil == ProfilUtilisateur.UGPM
                ? CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null)
                : ctx.idDossier() == null ? null
                        : dossierRepository.findById(ctx.idDossier()).map(cnm.prs.entity.Dossier::getIdPrmp).orElse(null);
        return mandats.mandatEnVigueur(idPrmp, LocalDate.now())
                .map(m -> m.getRefArrete() + " du " + m.getDateDebut().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                .orElse(null);
    }

    private static FicheMarche virtuelle(Long idDmc) {
        FicheMarche f = new FicheMarche();
        f.setIdDmc(idDmc);
        f.setNumeroVersion(1);
        f.setStatut(StatutFicheMarche.BROUILLON.name());
        f.setTypeMarche(TypeMarcheDao.QUANTITE_FIXE.name());
        f.setDateCreation(LocalDateTime.now());
        return f;
    }

    // ------------------------------------------------------------------ cadrage

    /**
     * Valide les réponses : clés connues (les clés de cadrage des champs de source CADRAGE, plus {@code attributaires}),
     * valeur typée selon le champ reflet (OUI/NON, nombre, pourcentage, option de liste). ⚠️ Lot 1c : {@code typeMarche}
     * n'est plus une réponse (dérivé de la forme du marché au plan) — la clé est ignorée si elle est encore envoyée.
     */
    Map<String, Object> validerCadrage(Map<String, Object> cadrage, String categorie, String typeMarche) {
        List<ErrorResponse.FieldError> erreurs = new ArrayList<>();
        // ⚠️ Lot 5 bis (2026-09-24, travaux) — le reflet qui valide une clé est d'abord celui de la catégorie et du type de
        // la fiche : plusieurs référentiels reflètent la même clé (alloti, typePrix, formeGroupement…), chacun à sa façon ;
        // le premier par code, toutes catégories confondues, aurait fait valider une fiche de fournitures par un reflet des
        // travaux. À défaut, un reflet quelconque de la clé.
        Map<String, ChampFicheMarche> reflets = new LinkedHashMap<>();
        Map<String, ChampFicheMarche> autres = new LinkedHashMap<>();
        for (ChampFicheMarche c : champRepository.findAllByOrderByCodeRubriqueAscRangAsc()) {
            if (SourceChampFiche.CADRAGE.name().equals(c.getSource()) && c.getCleCadrage() != null) {
                boolean dela = c.pourCategorie(categorie != null ? categorie : CategorieDao.FOURNITURES_SERVICES.name())
                        && c.pourTypeMarche(typeMarche);
                (dela ? reflets : autres).putIfAbsent(c.getCleCadrage(), c);
            }
        }
        autres.forEach(reflets::putIfAbsent);
        Map<String, Object> propre = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : cadrage.entrySet()) {
            String cle = e.getKey();
            Object valeur = e.getValue();
            if (valeur == null || String.valueOf(valeur).isBlank()) {
                continue;
            }
            String texte = String.valueOf(valeur).trim();
            if (CLE_TYPE_MARCHE.equals(cle)) {
                continue;   // lot 1c : dérivé du plan, plus une réponse — ignoré (tolérance d'une version), jamais un 400
            }
            if (CLE_TRANCHES.equals(cle)) {
                // ⚠️ Lot 5 (2026-09-24, §B5) — « Le marché comporte-t-il des tranches ? » : question des travaux seulement.
                String t = texte.toUpperCase();
                if (!CategorieDao.TRAVAUX.name().equals(categorie)) {
                    erreurs.add(new ErrorResponse.FieldError(cle, "La question des tranches ne vaut que pour les travaux."));
                } else if (!t.equals("OUI") && !t.equals("NON")) {
                    erreurs.add(new ErrorResponse.FieldError(cle, "« tranches » attend OUI ou NON."));
                } else {
                    propre.put(cle, t);
                }
                continue;
            }
            if ("attributaires".equals(cle)) {
                // ⚠️ Lot 4 (2026-09-23) — contrat-cadre mono ou multi-attributaire (conditions « attributaires = MONO |
                // MULTI » du référentiel), et non plus un nombre d'attributaires.
                String a = texte.toUpperCase();
                if (!a.equals("MONO") && !a.equals("MULTI")) {
                    erreurs.add(new ErrorResponse.FieldError(cle, "« attributaires » attend MONO ou MULTI."));
                } else {
                    propre.put(cle, a);
                }
                continue;
            }
            ChampFicheMarche reflet = reflets.get(cle);
            if (reflet == null) {
                erreurs.add(new ErrorResponse.FieldError(cle, "Clé de cadrage inconnue : " + cle + "."));
                continue;
            }
            String normalisee = normaliser(reflet, texte, erreurs, cle);
            if (normalisee != null) {
                propre.put(cle, TypeChampFiche.NOMBRE.name().equals(reflet.getType())
                        || TypeChampFiche.POURCENTAGE.name().equals(reflet.getType())
                        ? new BigDecimal(normalisee).stripTrailingZeros().scale() <= 0
                                ? (Object) new BigDecimal(normalisee).intValue() : new BigDecimal(normalisee)
                        : normalisee);
            }
        }
        if (!erreurs.isEmpty()) {
            throw new ChampsInvalidesException(erreurs);
        }
        return propre;
    }

    // ------------------------------------------------------------------ valeurs

    /** Valeur normalisée selon le type du champ ; {@code null} et une erreur nominative si elle ne se lit pas. */
    static String normaliser(ChampFicheMarche c, String brut, List<ErrorResponse.FieldError> erreurs, String champ) {
        TypeChampFiche type = TypeChampFiche.valueOf(c.getType());
        switch (type) {
            case OUI_NON -> {
                String v = brut.toUpperCase();
                if (v.equals("OUI") || v.equals("TRUE") || v.equals("1")) {
                    return "OUI";
                }
                if (v.equals("NON") || v.equals("FALSE") || v.equals("0")) {
                    return "NON";
                }
                erreurs.add(new ErrorResponse.FieldError(champ, "« " + c.getLibelle() + " » attend OUI ou NON."));
                return null;
            }
            case NOMBRE, MONTANT, POURCENTAGE -> {
                BigDecimal n = ControlesFicheMarche.nombre(brut);
                if (n == null) {
                    erreurs.add(new ErrorResponse.FieldError(champ, "« " + c.getLibelle() + " » attend un nombre."));
                    return null;
                }
                if (type == TypeChampFiche.MONTANT && n.signum() < 0) {
                    erreurs.add(new ErrorResponse.FieldError(champ, "« " + c.getLibelle() + " » : un montant n'est pas négatif."));
                    return null;
                }
                if (type == TypeChampFiche.POURCENTAGE && (n.signum() < 0 || n.compareTo(new BigDecimal("100")) > 0)) {
                    erreurs.add(new ErrorResponse.FieldError(champ, "« " + c.getLibelle() + " » : un pourcentage va de 0 à 100."));
                    return null;
                }
                return n.stripTrailingZeros().toPlainString();
            }
            case DATE -> {
                try {
                    return LocalDate.parse(brut).toString();
                } catch (DateTimeParseException e) {
                    erreurs.add(new ErrorResponse.FieldError(champ, "« " + c.getLibelle() + " » attend une date AAAA-MM-JJ."));
                    return null;
                }
            }
            case DATE_HEURE -> {   // ⚠️ V50 (2026-09-27, §B1.2) — ISO local à la minute
                LocalDateTime d = RemiseElectronique.dateHeure(brut);
                if (d == null) {
                    erreurs.add(new ErrorResponse.FieldError(champ, "« " + c.getLibelle() + " » attend une date et une heure AAAA-MM-JJTHH:MM."));
                    return null;
                }
                return RemiseElectronique.isoMinute(d);
            }
            case URL -> {   // ⚠️ V50 (§B1.2) — adresse absolue http / https, 500 caractères au plus
                if (!RemiseElectronique.urlValide(brut)) {
                    erreurs.add(new ErrorResponse.FieldError(champ, "« " + c.getLibelle() + " » attend une adresse http ou https"
                            + (brut.length() > RemiseElectronique.URL_LONGUEUR_MAX ? " (500 caractères au plus)" : "") + "."));
                    return null;
                }
                return brut;
            }
            case LISTE -> {
                List<String> options = ChampFicheMarche.options(c.getOptions());
                String v = options.stream().filter(o -> o.equalsIgnoreCase(brut)).findFirst().orElse(null);
                if (v == null && !options.isEmpty()) {
                    erreurs.add(new ErrorResponse.FieldError(champ, "« " + c.getLibelle() + " » attend une des options : "
                            + String.join(", ", options) + "."));
                    return null;
                }
                return v == null ? brut : v;
            }
            case LISTE_MULTIPLE -> {
                // ⚠️ V45 — plusieurs options : tableau JSON (servi par String.valueOf en « [A1, A2] ») ou chaîne séparée
                // par des virgules ; rangées dans l'ordre des options, sans doublon.
                List<String> options = ChampFicheMarche.options(c.getOptions());
                List<String> recues = ChampFicheMarche.liste(brut.replaceAll("^\\[|\\]$", "").replace(';', ','));
                List<String> inconnues = recues.stream()
                        .filter(r -> options.stream().noneMatch(o -> o.equalsIgnoreCase(r))).toList();
                if (!inconnues.isEmpty()) {
                    erreurs.add(new ErrorResponse.FieldError(champ, "« " + c.getLibelle() + " » : option(s) inconnue(s) "
                            + String.join(", ", inconnues) + " (attendu : " + String.join(", ", options) + ")."));
                    return null;
                }
                String v = options.stream().filter(o -> recues.stream().anyMatch(r -> r.equalsIgnoreCase(o)))
                        .collect(java.util.stream.Collectors.joining(","));
                return v.isEmpty() ? null : v;
            }
            case PIECE -> {
                erreurs.add(new ErrorResponse.FieldError(champ, "« " + c.getLibelle() + " » est une pièce : hors lot 1."));
                return null;
            }
            default -> {
                if (brut.length() > 4000) {
                    erreurs.add(new ErrorResponse.FieldError(champ, "« " + c.getLibelle() + " » : 4000 caractères au plus."));
                    return null;
                }
                return brut;
            }
        }
    }

    // ------------------------------------------------------------------ projection

    private FicheMarcheDto toDto(Contexte ctx, FicheMarche fiche) {
        Map<String, Object> cadrage = lireJson(fiche.getCadrage());
        // ⚠️ Lot 1c — le type de marché servi est DÉRIVÉ de la forme de la ligne courante (nul si le plan ne la porte
        // pas) ; les rubriques s'ouvrent selon lui, à défaut selon le type sous lequel la fiche a été saisie.
        String typeMarche = ctx.forme() == null ? null : ctx.forme().name();
        String typeOuverture = typeMarche != null ? typeMarche
                : fiche.getTypeMarche() != null ? fiche.getTypeMarche() : TypeMarcheDao.QUANTITE_FIXE.name();
        // ⚠️ Lot 5 — les champs s'ouvrent aussi selon la catégorie (à défaut : fournitures et services).
        String categorieOuverture = ctx.codeCategorie() != null ? ctx.codeCategorie() : CategorieDao.FOURNITURES_SERVICES.name();
        boolean typeChange = fiche.getIdFiche() != null && typeMarche != null && fiche.getTypeMarche() != null
                && !typeMarche.equals(fiche.getTypeMarche());
        if (typeChange && StatutFicheMarche.VALIDEE.name().equals(fiche.getStatut())) {
            log.warn("[FICHE_MARCHE] version validée sous un type qui n'est plus celui du plan : dmc={} version={} "
                    + "saisie={} plan={}", fiche.getIdDmc(), fiche.getNumeroVersion(), fiche.getTypeMarche(), typeMarche);
        }
        Map<String, String> valeurs = new TreeMap<>();
        List<String> champsCalcules = new ArrayList<>();   // V50 : les clés posées par le serveur
        if (fiche.getIdFiche() != null) {
            for (FicheMarcheValeur v : valeurRepository.findByIdFiche(fiche.getIdFiche())) {
                valeurs.put(v.getCodeChamp(), v.getValeur());
                if (Boolean.TRUE.equals(v.getCalculee())) {
                    champsCalcules.add(v.getCodeChamp());
                }
            }
            java.util.Collections.sort(champsCalcules);
        }
        ValeursPpmService.ValeursPpm ppm = valeursPpm.lire(ctx.idDetail());
        int nbLots = LotsFiche.nbLots(ppm.valeurs());   // 2026-09-25 (§B2) : les champs par lot d'une ligne allotie

        List<ChampFicheMarche> ouverts = new ArrayList<>();
        Map<String, String> enLettres = new TreeMap<>();
        Map<String, String> valeursCadrage = new TreeMap<>();
        Map<String, String> valeursPpmParCode = new TreeMap<>();
        for (ChampFicheMarche c : champRepository.findByActifTrueOrderByCodeRubriqueAscRangAsc()) {
            if (!c.pourTypeMarche(typeOuverture) || !c.pourCategorie(categorieOuverture) || !ConditionCadrage.vraie(c.getCondition(), cadrage)) {
                continue;
            }
            ouverts.add(c);
            if (SourceChampFiche.PPM.name().equals(c.getSource()) && c.getClePpm() != null) {
                valeursPpmParCode.put(c.getCode(), ppm.valeurs().get(c.getClePpm()));
            } else if (SourceChampFiche.CADRAGE.name().equals(c.getSource()) && c.getCleCadrage() != null) {
                Object r = cadrage.get(c.getCleCadrage());
                if (r == null && RemiseElectronique.CLE_CADRAGE.equals(c.getCleCadrage())) {
                    r = RemiseElectronique.PAPIER;   // ⚠️ V50 (§B1.1) — une fiche sans clé modeRemise est lue comme PAPIER
                }
                valeursCadrage.put(c.getCode(), r == null ? null : String.valueOf(r));
            } else if (TypeChampFiche.MONTANT.name().equals(c.getType())) {
                for (String cle : LotsFiche.cles(c, nbLots)) {
                    BigDecimal m = ControlesFicheMarche.nombre(valeurs.get(cle));
                    if (m != null) {
                        enLettres.put(cle, MontantEnLettres.ariary(m));
                    }
                }
            }
        }
        // ⚠️ V45 (2026-09-25, §B4) — le besoin (fournitures) et le taux de garantie administrable entrent au bilan.
        ControlesFicheMarche.Besoin besoinBilan = besoinApplicable(ctx)
                ? new ControlesFicheMarche.Besoin(besoin.articles(fiche.getIdFiche()),
                        TypeMarcheDao.A_COMMANDE.name().equals(typeOuverture),
                        CategorieDao.TRAVAUX.name().equals(ctx.codeCategorie()))
                : null;
        // ⚠️ V50 (2026-09-27, remise électronique) — le bilan lit aussi le mode, les paramètres administrables, les
        // paramètres internes et le responsable de la procédure (règles 1 à 11, mode électronique seulement).
        Long idDmc = fiche.getIdDmc();
        BilanControlesDto bilan = ControlesFicheMarche.bilan(ouverts, valeurs, cadrage, ppm.dates(), nbLots, besoinBilan,
                parametres.tauxGarantie(), internes.contexteBilan(idDmc, cadrage), categorieOuverture);
        // ⚠️ V60 (2026-10-03, §B3) — une fiche de travaux dit son matériel : la liste, ou le texte B03-QT-09.
        if (CategorieDao.TRAVAUX.name().equals(ctx.codeCategorie())) {
            ControlesFicheMarche.materielExige(ouverts, valeurs, moyens.materiel(fiche.getIdFiche()).size(), bilan);
            // ⚠️ V61 (2026-10-03, §B3) — les pièces de l'offre (liste OFFRE ou B04-PI-01) ; doublon avec le défaut de B03-CQ-01.
            List<cnm.prs.dto.PieceExigeeDto> lues = pieces.pieces(fiche.getIdFiche());
            ControlesFicheMarche.piecesOffreExigees(ouverts, valeurs, PiecesFiche.compter(lues, PiecesFiche.OFFRE), bilan);
            ControlesFicheMarche.piecesEnDouble(ouverts, valeurs, PiecesFiche.compter(lues, PiecesFiche.ADMINISTRATIVE), bilan);
        }
        return new FicheMarcheDto(fiche.getIdFiche(), fiche.getIdDmc(), ctx.idDetail(), ctx.idDossier(),
                ppm.ligne() == null ? ctx.idDetail() : ppm.ligne().getIdDetail(),
                ppm.ligne() != null && Boolean.TRUE.equals(ppm.ligne().getSupprimee()),
                ppm.valeurs().get("DOSSIER_REFERENCE"), ppm.ligne() == null ? null : ppm.ligne().getDesignationMarche(),
                fiche.getNumeroVersion(), fiche.getStatut(), typeMarche, cadrage, valeurs, enLettres, valeursCadrage,
                valeursPpmParCode, ppm.versionPpm(), bilan, fiche.getDateCreation(), fiche.getDateMaj(),
                fiche.getDateValidation(), fiche.getValidePar(),
                dossierRepository.findIdDossierByIdDmc(fiche.getIdDmc()).orElse(null),
                fiche.getIdFiche() != null && StatutFicheMarche.BROUILLON.name().equals(fiche.getStatut()) && typeChange,
                ctx.outille(), ctx.codeCategorie(), nbLots, LotsFiche.alloti(nbLots),
                internes.responsableDto(idDmc), internes.estTitulaire(idDmc), internes.etat(idDmc).name(), champsCalcules);
    }

    /**
     * ⚠️ V45 — le besoin par article vaut pour une fiche de fournitures et services (catégorie connue) ; ⚠️ V59
     * (2026-10-02, §B1.1) — et pour une fiche de travaux.
     */
    private static boolean besoinApplicable(Contexte ctx) {
        return CategorieDao.FOURNITURES_SERVICES.name().equals(ctx.codeCategorie())
                || CategorieDao.TRAVAUX.name().equals(ctx.codeCategorie());
    }

    /** Le cadrage enregistré, sans l'ancienne clé {@code typeMarche} (lot 1c : elle n'est plus une réponse). */
    private Map<String, Object> lireJson(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        LinkedHashMap<String, Object> cadrage = mapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
        });
        cadrage.remove(CLE_TYPE_MARCHE);
        return cadrage;
    }

    private String ecrireJson(Map<String, Object> cadrage) {
        return mapper.writeValueAsString(cadrage);
    }
}
