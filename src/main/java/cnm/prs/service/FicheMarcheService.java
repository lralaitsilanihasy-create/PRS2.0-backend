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

    public FicheMarcheService(FicheMarcheRepository ficheRepository, FicheMarcheValeurRepository valeurRepository,
            ChampFicheMarcheRepository champRepository, BlocFicheMarcheRepository blocRepository,
            DossierMecRepository dmcRepository, MarcheRepository marcheRepository, TypeDmcRepository typeDmcRepository,
            PerimetreDossier perimetre, ValeursPpmService valeursPpm, DossierIntegriteService dossierIntegrite,
            JournalDossierService journal, ObjectMapper mapper,
            cnm.prs.repository.DossierRepository dossierRepository, DocumentsFicheMarcheService documents,
            cnm.prs.repository.DocumentFicheMarcheRepository documentRepository, DmcService dmcService) {
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
        Contexte ctx = contexte(idDmc, false);
        FicheMarche fiche = ficheRepository.findFirstByIdDmcOrderByNumeroVersionDesc(idDmc).orElseGet(() -> virtuelle(idDmc));
        FicheMarcheDto d = toDto(ctx, fiche);
        return new FicheMarcheResumeDto(idDmc, d.getIdDetail(), d.getRefeDossier(), d.getDesignationMarche(),
                d.getTypeMarche(), d.getStatut(), d.getVersion(), d.getBilanControles().nbSaisis(),
                d.getBilanControles().nbAttendus());
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
        contexte(idDmc);
        FicheMarche fiche = version == null
                ? ficheRepository.findFirstByIdDmcOrderByNumeroVersionDesc(idDmc).orElse(null)
                : ficheRepository.findByIdDmcAndNumeroVersion(idDmc, version).orElseThrow(
                        () -> new ResourceNotFoundException("Version " + version + " introuvable pour le DMC " + idDmc + "."));
        return documents.lister(fiche);
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

    // ------------------------------------------------------------------ écritures

    public FicheMarcheDto ecrireCadrage(Long idDmc, Map<String, Object> cadrage) {
        Contexte ctx = contexteEcriture(idDmc);
        Map<String, Object> propre = validerCadrage(cadrage == null ? Map.of() : cadrage, ctx.codeCategorie());
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

        List<ErrorResponse.FieldError> erreurs = new ArrayList<>();
        Map<String, String> aEcrire = new TreeMap<>();
        for (Map.Entry<String, Object> e : (valeurs == null ? Map.<String, Object>of() : valeurs).entrySet()) {
            String code = e.getKey() == null ? "" : e.getKey().trim().toUpperCase();
            ChampFicheMarche c = champs.get(code);
            if (c == null || !Boolean.TRUE.equals(c.getActif())) {
                erreurs.add(new ErrorResponse.FieldError(code, "Champ inconnu ou inactif : " + code + "."));
                continue;
            }
            if (!codeBloc.equals(c.codeBloc())) {
                erreurs.add(new ErrorResponse.FieldError(code, "Le champ " + code + " n'appartient pas au bloc " + codeBloc + "."));
                continue;
            }
            if (!SourceChampFiche.SAISIE.name().equals(c.getSource())) {
                erreurs.add(new ErrorResponse.FieldError(code, "« " + c.getLibelle() + " » est "
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
            String normalisee = normaliser(c, brut, erreurs);
            if (normalisee != null) {
                aEcrire.put(code, normalisee);
            }
        }
        if (!erreurs.isEmpty()) {
            throw new ChampsInvalidesException(erreurs);
        }
        valeurRepository.deleteParBloc(fiche.getIdFiche(), codeBloc + "-%");
        for (Map.Entry<String, String> e : aEcrire.entrySet()) {
            valeurRepository.save(new FicheMarcheValeur(null, fiche.getIdFiche(), e.getKey(), e.getValue()));
        }
        fiche.setDateMaj(LocalDateTime.now());
        fiche = ficheRepository.save(fiche);
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
        List<DocumentsFicheMarcheService.Produit> produits = documents.produire(etat, maintenant);
        fiche.setStatut(StatutFicheMarche.VALIDEE.name());
        fiche.setDateValidation(maintenant);
        fiche.setValidePar(CurrentUser.ref().orElse(null));
        fiche = ficheRepository.save(fiche);
        documents.enregistrer(fiche.getIdFiche(), produits, maintenant);
        journal.tracer(ctx.idDossier(), JournalDossierService.FICHE_MARCHE_VALIDEE,
                "DAO, version " + fiche.getNumeroVersion() + ", " + bilan.nbSaisis() + " information(s)");
        // Le dossier soumis que porte déjà la fiche reçoit les documents de cette version (lot 2a, §B3/§B4).
        Long idDmcValide = idDmc;
        dossierRepository.findIdDossierByIdDmc(idDmc).ifPresent(id -> documents.joindre(id, idDmcValide));
        return toDto(ctx, fiche);
    }

    /** Ouvre la version suivante en brouillon, copie de la dernière validée. */
    public FicheMarcheDto reviser(Long idDmc) {
        Contexte ctx = contexteEcriture(idDmc);
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
        for (FicheMarcheValeur v : valeurRepository.findByIdFiche(derniere.getIdFiche())) {
            valeurRepository.save(new FicheMarcheValeur(null, suivante.getIdFiche(), v.getCodeChamp(), v.getValeur()));
        }
        return toDto(ctx, suivante);
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
            return ficheRepository.save(f);
        }
        if (StatutFicheMarche.VALIDEE.name().equals(derniere.getStatut())) {
            throw new BusinessRuleException("La version " + derniere.getNumeroVersion() + " est validée, donc figée : "
                    + "ouvrez une révision (POST /reviser) pour la modifier.", "FICHE_VALIDEE");
        }
        return derniere;
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
    private Map<String, Object> validerCadrage(Map<String, Object> cadrage, String categorie) {
        List<ErrorResponse.FieldError> erreurs = new ArrayList<>();
        Map<String, ChampFicheMarche> reflets = new LinkedHashMap<>();
        for (ChampFicheMarche c : champRepository.findAllByOrderByCodeRubriqueAscRangAsc()) {
            if (SourceChampFiche.CADRAGE.name().equals(c.getSource()) && c.getCleCadrage() != null) {
                reflets.putIfAbsent(c.getCleCadrage(), c);
            }
        }
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
            if (CLE_TRANCHES.equals(cle) && !reflets.containsKey(cle)) {
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

    private static String normaliser(ChampFicheMarche c, String brut, List<ErrorResponse.FieldError> erreurs) {
        return normaliser(c, brut, erreurs, c.getCode());
    }

    /** Valeur normalisée selon le type du champ ; {@code null} et une erreur nominative si elle ne se lit pas. */
    private static String normaliser(ChampFicheMarche c, String brut, List<ErrorResponse.FieldError> erreurs, String champ) {
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
            case LISTE -> {
                List<String> options = ChampFicheMarche.liste(c.getOptions());
                String v = options.stream().filter(o -> o.equalsIgnoreCase(brut)).findFirst().orElse(null);
                if (v == null && !options.isEmpty()) {
                    erreurs.add(new ErrorResponse.FieldError(champ, "« " + c.getLibelle() + " » attend une des options : "
                            + String.join(", ", options) + "."));
                    return null;
                }
                return v == null ? brut : v;
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
        if (fiche.getIdFiche() != null) {
            valeurRepository.findByIdFiche(fiche.getIdFiche()).forEach(v -> valeurs.put(v.getCodeChamp(), v.getValeur()));
        }
        ValeursPpmService.ValeursPpm ppm = valeursPpm.lire(ctx.idDetail());

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
                valeursCadrage.put(c.getCode(), r == null ? null : String.valueOf(r));
            } else if (TypeChampFiche.MONTANT.name().equals(c.getType()) && valeurs.get(c.getCode()) != null) {
                BigDecimal m = ControlesFicheMarche.nombre(valeurs.get(c.getCode()));
                if (m != null) {
                    enLettres.put(c.getCode(), MontantEnLettres.ariary(m));
                }
            }
        }
        BilanControlesDto bilan = ControlesFicheMarche.bilan(ouverts, valeurs, cadrage, ppm.dates());
        return new FicheMarcheDto(fiche.getIdFiche(), fiche.getIdDmc(), ctx.idDetail(), ctx.idDossier(),
                ppm.ligne() == null ? ctx.idDetail() : ppm.ligne().getIdDetail(),
                ppm.ligne() != null && Boolean.TRUE.equals(ppm.ligne().getSupprimee()),
                ppm.valeurs().get("DOSSIER_REFERENCE"), ppm.ligne() == null ? null : ppm.ligne().getDesignationMarche(),
                fiche.getNumeroVersion(), fiche.getStatut(), typeMarche, cadrage, valeurs, enLettres, valeursCadrage,
                valeursPpmParCode, ppm.versionPpm(), bilan, fiche.getDateCreation(), fiche.getDateMaj(),
                fiche.getDateValidation(), fiche.getValidePar(),
                dossierRepository.findIdDossierByIdDmc(fiche.getIdDmc()).orElse(null),
                fiche.getIdFiche() != null && StatutFicheMarche.BROUILLON.name().equals(fiche.getStatut()) && typeChange,
                ctx.outille(), ctx.codeCategorie());
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
