package cnm.prs.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.DmcDto;
import cnm.prs.dto.LigneEligibleDto;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.DossierMec;
import cnm.prs.entity.Marche;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.TypeDmc;
import cnm.prs.enums.FormeMarche;
import cnm.prs.enums.StatutDmc;
import cnm.prs.enums.StatutDossier;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.DmcMapper;
import cnm.prs.repository.ChampFicheMarcheRepository;
import cnm.prs.repository.DossierMecRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.ModePassationRepository;
import cnm.prs.repository.TypeDmcRepository;
import cnm.prs.security.CurrentUser;
import cnm.prs.security.PerimetreDossier;
import cnm.prs.security.Visibilite;

/**
 * Dossier de mise en concurrence (DMC) : création <strong>par ligne de marché</strong> avec type
 * <strong>dérivé du mode de passation</strong> (mapping en base, pas d'enum codé en dur). Service
 * dédié, non câblé automatiquement sur la saisie/soumission (déclenchement explicite).
 *
 * <p>⚠️ Fiche marché DAO (demande front du 2026-09-22, §B2) — la création s'ouvre à la <strong>PRMP et à son
 * UGPM</strong>, pour leurs propres lignes, sous les gardes H4 (voir {@link #motifInegibilite}) ; l'Administrateur
 * garde son geste d'origine (sans H4). {@code GET /eligibles} liste les lignes qui passent ces gardes.</p>
 */
@Service
@Transactional
public class DmcService {

    /** Code du type de DMC d'un appel d'offres (H4 : « mode mappé au type DAO »). */
    public static final String TYPE_DAO = "DAO";

    /**
     * ⚠️ Lot 1c (2026-09-23, §B2) — les formes de marché que la fiche marché sait préparer. Constante et non colonne :
     * elle s'allonge sans migration. ⚠️ Lots 3 et 4 (2026-09-23) : à commande et contrat-cadre ouverts — les trois
     * formes le sont ; la garde {@code FORME_NON_OUTILLEE} ne répond plus que pour une ligne sans forme.
     */
    public static final java.util.Set<FormeMarche> FORMES_OUTILLEES = java.util.EnumSet.of(FormeMarche.QUANTITE_FIXE,
            FormeMarche.A_COMMANDE, FormeMarche.CONTRAT_CADRE);

    /**
     * ⚠️ Lot 1c — le refus {@code FORME_NON_OUTILLEE} d'une ligne : forme absente du plan (message qui nomme le champ
     * à compléter) ou non outillée (message qui la nomme). Vide si la forme est outillée. Partagé par la création du
     * DMC, la liste des éligibles et les écritures de la fiche.
     */
    public static Optional<Motif> motifForme(FormeMarche forme) {
        if (forme == null) {
            return Optional.of(new Motif("FORME_NON_OUTILLEE", "La forme du marché n'est pas renseignée dans le plan : "
                    + "complétez le champ « Forme du marché » de la ligne avant de préparer l'appel d'offres."));
        }
        if (!FORMES_OUTILLEES.contains(forme)) {
            String nom = forme == FormeMarche.CONTRAT_CADRE ? "un contrat-cadre" : "un marché à commande";
            return Optional.of(new Motif("FORME_NON_OUTILLEE", "Cette ligne est " + nom + " ; la fiche marché ne prend en "
                    + "charge que les marchés à quantité fixe pour le moment."));
        }
        return Optional.empty();
    }

    /**
     * ⚠️ Lot 5 (2026-09-24) — les catégories de fiche DAO que la fiche sait préparer. Constante, comme
     * {@link #FORMES_OUTILLEES} : elle s'allongera avec le contenu des travaux, puis des prestations intellectuelles.
     */
    public static final java.util.Set<cnm.prs.enums.CategorieDao> CATEGORIES_OUTILLEES =
            java.util.EnumSet.of(cnm.prs.enums.CategorieDao.FOURNITURES_SERVICES,
            cnm.prs.enums.CategorieDao.TRAVAUX,   // ⚠️ 2026-09-24 : travaux ouverts (référentiel V41 + import)
            cnm.prs.enums.CategorieDao.PRESTATIONS_INTELLECTUELLES);   // ⚠️ 2026-09-24 : V42 + import

    /** La catégorie d'une ligne (nulle si le plan ou le référentiel ne la donne pas) et le refus qu'elle vaut, s'il y a lieu. */
    public record CategorieLigne(cnm.prs.enums.CategorieDao categorie, Optional<Motif> motif) {
    }

    /**
     * ⚠️ Lot 5 — la catégorie de la ligne, lue sur sa nature. Refus {@code FORME_NON_OUTILLEE} (même code que la forme,
     * §B2 test 6) : nature absente de la ligne, nature sans catégorie au référentiel (à compléter par l'Administrateur),
     * ou catégorie non outillée.
     */
    public CategorieLigne categorie(Marche ligne) {
        return categorie(ligne, new HashMap<>());
    }

    private CategorieLigne categorie(Marche ligne, Map<Integer, java.util.Optional<cnm.prs.entity.Nature>> cache) {
        if (ligne == null || ligne.getIdNature() == null) {
            return new CategorieLigne(null, Optional.of(new Motif("FORME_NON_OUTILLEE", "La nature du marché n'est pas "
                    + "renseignée dans le plan : complétez le champ « Nature » de la ligne avant de préparer l'appel d'offres.")));
        }
        cnm.prs.entity.Nature nature = cache.computeIfAbsent(ligne.getIdNature(), natureRepository::findById).orElse(null);
        cnm.prs.enums.CategorieDao cat = nature == null || nature.getCategorieDao() == null ? null
                : cnm.prs.enums.CategorieDao.valueOf(nature.getCategorieDao());
        if (cat == null) {
            String nom = nature == null ? String.valueOf(ligne.getIdNature()) : nature.getLibelle();
            return new CategorieLigne(null, Optional.of(new Motif("FORME_NON_OUTILLEE", "La nature « " + nom + " » n'a "
                    + "pas de catégorie de fiche DAO : à compléter par l'Administrateur (référentiel des natures).")));
        }
        if (!CATEGORIES_OUTILLEES.contains(cat)) {
            return new CategorieLigne(cat, Optional.of(new Motif("FORME_NON_OUTILLEE", "Cette ligne est de catégorie « "
                    + cat.libelle() + " » ; la fiche DAO ne prend en charge que les fournitures et services pour le moment.")));
        }
        return new CategorieLigne(cat, Optional.empty());
    }

    private final DossierMecRepository repository;
    private final MarcheRepository marcheRepository;
    private final ModePassationRepository modeRepository;
    private final TypeDmcRepository typeDmcRepository;
    private final PerimetreDossier perimetre;
    private final DossierRepository dossierRepository;
    private final DossierIntegriteService dossierIntegrite;
    private final ValeursPpmService valeursPpm;
    private final ChampFicheMarcheRepository champRepository;
    /** ⚠️ Lot 5 (2026-09-24) — la catégorie d'une ligne se lit sur sa nature ({@code tr_nature.CATEGORIE_DAO}). */
    private final cnm.prs.repository.NatureRepository natureRepository;

    /** ⚠️ 2026-09-27 (statut « Lancé ») — le journal du plan reçoit la création du DMC et le statut de la ligne. */
    private final JournalDossierService journal;
    /** ⚠️ 2026-09-27 — le référentiel des statuts : LANCE y est remis s'il en avait disparu (seul PREVU est indestructible). */
    private final cnm.prs.repository.StatutMarcheRepository statutMarcheRepository;

    /** ⚠️ 2026-09-30 — les avis spécifiques imprimés (date de lancement d'une ligne). */
    private final cnm.prs.repository.DocumentFicheMarcheRepository documentRepository;

    /** ⚠️ 2026-10-06 (compteurs des reçus et des offres, §B2). */
    private final cnm.prs.repository.RecuDaoRepository recuRepository;
    private final cnm.prs.repository.OffreRepository offreRepository;

    /** ⚠️ 2026-10-19 (« etat-fiche-liste », §B1 ; « dossier-marche-lecture », §B2). */
    private final cnm.prs.repository.FicheMarcheRepository ficheRepository;
    private final cnm.prs.repository.AttributionRepository attributionRepository;
    /** ⚠️ 2026-10-20 (« liste-dossier-marche ») — le numéro de la procédure (§B1), l'attribution par lot (§B2). */
    private final cnm.prs.repository.FicheMarcheValeurRepository valeurRepository;
    private final cnm.prs.repository.EvaluationRepository evaluationRepository;
    private final org.springframework.beans.factory.ObjectProvider<AttributionService> attributionService;

    public DmcService(DossierMecRepository repository, MarcheRepository marcheRepository,
            ModePassationRepository modeRepository, TypeDmcRepository typeDmcRepository,
            PerimetreDossier perimetre, DossierRepository dossierRepository,
            DossierIntegriteService dossierIntegrite, ValeursPpmService valeursPpm,
            ChampFicheMarcheRepository champRepository, cnm.prs.repository.NatureRepository natureRepository,
            JournalDossierService journal, cnm.prs.repository.StatutMarcheRepository statutMarcheRepository,
            cnm.prs.repository.DocumentFicheMarcheRepository documentRepository, cnm.prs.repository.RecuDaoRepository recuRepository,
            cnm.prs.repository.OffreRepository offreRepository, cnm.prs.repository.FicheMarcheRepository ficheRepository,
            cnm.prs.repository.AttributionRepository attributionRepository,
            cnm.prs.repository.FicheMarcheValeurRepository valeurRepository, cnm.prs.repository.EvaluationRepository evaluationRepository,
            org.springframework.beans.factory.ObjectProvider<AttributionService> attributionService) {
        this.valeurRepository = valeurRepository;
        this.evaluationRepository = evaluationRepository;
        this.attributionService = attributionService;
        this.ficheRepository = ficheRepository;
        this.attributionRepository = attributionRepository;
        this.recuRepository = recuRepository;
        this.offreRepository = offreRepository;
        this.documentRepository = documentRepository;
        this.journal = journal;
        this.statutMarcheRepository = statutMarcheRepository;
        this.natureRepository = natureRepository;
        this.repository = repository;
        this.marcheRepository = marcheRepository;
        this.modeRepository = modeRepository;
        this.typeDmcRepository = typeDmcRepository;
        this.perimetre = perimetre;
        this.dossierRepository = dossierRepository;
        this.dossierIntegrite = dossierIntegrite;
        this.valeursPpm = valeursPpm;
        this.champRepository = champRepository;
    }

    /**
     * Crée le DMC d'une ligne de marché, son type dérivé du mode de passation.
     *
     * <p>Administrateur : <strong>400</strong> si le mode n'est pas mappé à un type actif (message de configuration),
     * <strong>409</strong> {@code DAO_EXISTANT} si la ligne a déjà un DMC (relation 1-1). PRMP / UGPM (2026-09-22) :
     * dans l'ordre, <strong>403</strong> hors de ses dossiers (avant toute autre garde : l'existence d'un dossier
     * d'autrui ne se devine pas), 409 {@code VACANCE_PRMP} sans mandat actif, puis les 409 nominatifs de H4
     * ({@code LIGNE_RETIREE}, {@code MODE_NON_DAO}, {@code PV_NON_SIGNE}, {@code DAO_EXISTANT}). La réponse porte
     * les informations reprises du PPM ({@code valeursPpm}, {@code versionPpm}).</p>
     */
    public DmcDto creerPourMarche(Integer idDetail) {
        Marche marche = marcheRepository.findById(idDetail)
                .orElseThrow(() -> new ResourceNotFoundException("Marché introuvable : " + idDetail));
        TypeDmc type;
        if (Visibilite.estPrmp()) {
            String ref = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
            if (ref == null || marche.getIdDossier() == null
                    || !dossierRepository.existsVisiblePourPrmp(marche.getIdDossier(), ref)) {
                throw new AccessDeniedException("Cette ligne de marché n'est pas dans un de vos plans.");
            }
            dossierIntegrite.exigerMandatActif();
            Dossier dossier = dossierRepository.findById(marche.getIdDossier()).orElse(null);
            motifInegibilite(marche, dossier, new Caches(null), true).ifPresent(m -> {
                throw new BusinessRuleException(m.message(), m.code());
            });
            type = typeDmcRepository.findByCode(TYPE_DAO).orElseThrow();
        } else {
            if (repository.existsByIdDetail(idDetail)) {
                throw new BusinessRuleException("La ligne de marché " + idDetail + " a déjà un DMC.", "DAO_EXISTANT");
            }
            type = resoudreType(marche);
        }

        DossierMec dmc = new DossierMec();
        dmc.setIdDetail(idDetail);
        dmc.setIdTypeDmc(type.getIdTypeDmc());
        dmc.setStatut(StatutDmc.A_PREPARER);
        dmc.setDateCreation(LocalDateTime.now());
        DossierMec saved = repository.save(dmc);
        saved.setTypeDmc(type);   // pour l'affichage code/libellé (association lecture seule)
        // ⚠️ 2026-09-30 (décision du pilote, demande front « Lancé à l'avis spécifique », §B1) — la création du DMC ne
        // touche plus le statut : la fiche DAO et son examen sont la PRÉPARATION, la ligne reste « Prévu ». Elle passe
        // « Lancé » à la première impression de son avis spécifique (AvisSpecifiqueService#lancerLigne). La règle du
        // 27/09 (LANCE posé ici, journal LIGNE_LANCEE) est retirée.
        DmcDto dto = DmcMapper.toDto(saved);
        dto.setStatutLigne(marche.getStatut());
        ValeursPpmService.ValeursPpm ppm = valeursPpm.lire(idDetail);
        // Clé = code du champ de source PPM (comme sur la fiche), valeur telle qu'affichée.
        Map<String, String> parCode = new java.util.TreeMap<>();
        champRepository.findByActifTrueOrderByCodeRubriqueAscRangAsc().stream()
                .filter(c -> "PPM".equals(c.getSource()) && c.getClePpm() != null)
                .forEach(c -> parCode.put(c.getCode(), ppm.valeurs().get(c.getClePpm())));
        dto.setValeursPpm(parCode);
        dto.setVersionPpm(ppm.versionPpm());
        return dto;
    }

    /**
     * ⚠️ 2026-09-22 (§B2, H4) — les lignes de PPM éligibles à un appel d'offres pour l'utilisateur courant : ses
     * plans (PRMP / UGPM), tout (Président / Administrateur), rien pour les autres profils. Une ligne est éligible
     * si {@link #motifInegibilite} ne lui trouve rien — sauf {@code DAO_EXISTANT}, qui la garde dans la liste avec
     * {@code dejaDao = true} et l'{@code idDmc} à rouvrir.
     */
    @Transactional(readOnly = true)
    public List<LigneEligibleDto> eligibles() {
        return eligibles(null);
    }

    /**
     * ⚠️ 2026-10-14 (demande front « sous-type-ligne », §B2) — les lignes éligibles dont la fiche produira le sous-type demandé
     * ({@code null} ou vide : toutes) ; 400 {@code SOUS_TYPE_INCONNU} hors des sous-types qu'une fiche produit.
     */
    @Transactional(readOnly = true)
    public List<LigneEligibleDto> eligibles(String sousType) {
        String filtre = sousType == null || sousType.isBlank() ? null : sousType.trim().toUpperCase(java.util.Locale.ROOT);
        if (filtre != null && !SousTypesDossier.PRODUITS_PAR_FICHE.contains(filtre)) {
            throw new BadRequestException("Sous-type « " + sousType + " » inconnu : attendu l'un de "
                    + new java.util.TreeSet<>(SousTypesDossier.PRODUITS_PAR_FICHE) + ".", "SOUS_TYPE_INCONNU");
        }
        List<Marche> lignes;
        if (Visibilite.voitTout()) {
            lignes = marcheRepository.findAll();
        } else if (Visibilite.estPrmp()) {
            lignes = CurrentUser.ref().filter(s -> !s.isBlank()).map(marcheRepository::findVisiblesPourPrmp)
                    .orElseGet(List::of);
        } else {
            return List.of();
        }
        // Une seule requête pour « dejaDao » sur la filiation (pas une par ligne — leçon du référentiel des délais).
        Map<Integer, DossierMec> dmcParOrigine = new HashMap<>();
        for (Object[] row : repository.findAvecOrigine()) {
            dmcParOrigine.putIfAbsent((Integer) row[0], (DossierMec) row[1]);
        }
        Caches caches = new Caches(dmcParOrigine);
        // ⚠️ 2026-10-06 (compteurs, §B2) — reçus en attente et offres déposées : une requête chacun pour toute la liste.
        Map<Long, Long> recusEnAttente = new HashMap<>();
        recuRepository.compterEnAttenteParDmc().forEach(r -> recusEnAttente.put((Long) r[0], (Long) r[1]));
        Map<Long, Long> nbOffres = new HashMap<>();
        offreRepository.compterDeposeesParDmc().forEach(r -> nbOffres.put((Long) r[0], (Long) r[1]));
        // ⚠️ 2026-10-19 (« etat-fiche-liste », §B1) — état de la fiche et de son dossier : une requête chacun.
        Map<Long, LigneEligibleDto.EtatFiche> etatsFiche = new HashMap<>();
        ficheRepository.findEtatsDernieresVersions().forEach(
                r -> etatsFiche.put((Long) r[0], new LigneEligibleDto.EtatFiche((String) r[2], (Integer) r[1])));
        Map<Long, LigneEligibleDto.EtatDossier> etatsDossier = new HashMap<>();
        dossierRepository.findEtatsDossiersNesDeFiche().forEach(r -> etatsDossier.put((Long) r[0],
                new LigneEligibleDto.EtatDossier((Integer) r[1], (String) r[2], (String) r[3])));
        // ⚠️ 2026-10-19 (« dossier-marche-lecture », §B2) — les dossiers de marché par fiche : une requête de plus.
        Map<Long, List<LigneEligibleDto.DossierMarche>> dossiersMarche = new HashMap<>();
        attributionRepository.findDossiersMarche().forEach(r -> dossiersMarche.computeIfAbsent((Long) r[0], k -> new ArrayList<>())
                .add(new LigneEligibleDto.DossierMarche((Integer) r[1], (Integer) r[2], (String) r[3], (String) r[4])));
        // ⚠️ 2026-10-20 (« liste-dossier-marche », §B1) — les numéros de procédure : une requête ; §B2 : l'attribution, pour les seules
        // procédures dont l'évaluation est ouverte (une requête pour les repérer), par la règle de l'écran d'attribution.
        Map<Long, String> numeros = new HashMap<>();
        Map<Long, String> numerosDao = new HashMap<>();
        for (Object[] r : valeurRepository.findNumerosProcedure()) {
            String v = r[3] == null ? null : ((String) r[3]).trim();
            if (v == null || v.isEmpty()) {
                continue;
            }
            if ("B02-OE-01".equals(r[2]) && cnm.prs.enums.TypeMarcheDao.CONTRAT_CADRE.name().equals(r[1])) {
                numeros.put((Long) r[0], v);
            } else if ("B02-OB-03".equals(r[2])) {
                numerosDao.put((Long) r[0], v);
            }
        }
        numerosDao.forEach(numeros::putIfAbsent);
        java.util.Set<Long> evaluees = new java.util.HashSet<>(evaluationRepository.findIdsDmc());
        Map<Integer, Dossier> dossiers = new HashMap<>();
        List<LigneEligibleDto> out = new ArrayList<>();
        for (Marche m : lignes) {
            Dossier d = m.getIdDossier() == null ? null
                    : dossiers.computeIfAbsent(m.getIdDossier(), id -> dossierRepository.findById(id).orElse(null));
            Optional<Motif> motif = motifInegibilite(m, d, caches, false);
            if (motif.isPresent() && !"DAO_EXISTANT".equals(motif.get().code())) {
                continue;
            }
            ModePassation mode = caches.mode(m.getIdMode());
            DossierMec dmc = dmcParOrigine.get(m.getIdLigneOrigine());
            CategorieLigne cat = categorie(m, caches.natures);
            // ⚠️ 2026-09-29 — la nature vient du cache que categorie() vient de remplir : aucune requête de plus.
            cnm.prs.entity.Nature nature = m.getIdNature() == null ? null
                    : caches.natures.getOrDefault(m.getIdNature(), Optional.empty()).orElse(null);
            // ⚠️ 2026-10-14 (§B1) — la règle de la validation, sur le mode et la catégorie de la ligne en vigueur.
            String sousTypeLigne = SousTypesDossier.dossierMiseEnConcurrence(mode == null ? null : mode.getLibelle(),
                    cat.categorie() == cnm.prs.enums.CategorieDao.PRESTATIONS_INTELLECTUELLES,
                    m.formeMarcheSaisie() == cnm.prs.enums.FormeMarche.CONTRAT_CADRE);   // ⚠️ 2026-10-18 (« sous-type-contrat-cadre », §B1) : DAOO-CC
            if (filtre != null && !filtre.equals(sousTypeLigne)) {
                continue;
            }
            out.add(new LigneEligibleDto(m.getIdDetail(), m.getIdDossier(), d == null ? null : d.getRefeDossier(),
                    m.getDesignationMarche(), m.getIdMode(), mode == null ? null : mode.getLibelle(), m.getMontEstim(),
                    dmc != null, dmc == null ? null : dmc.getIdDmc(),
                    m.formeMarcheSaisie() == null ? null : m.formeMarcheSaisie().name(),
                    motifForme(m.formeMarcheSaisie()).isEmpty(), cat.categorie() == null ? null : cat.categorie().name(),
                    cat.motif().isEmpty(), m.getIdNature(), nature == null ? null : nature.getLibelle(),
                    dmc == null ? 0 : recusEnAttente.getOrDefault(dmc.getIdDmc(), 0L), dmc == null ? 0 : nbOffres.getOrDefault(dmc.getIdDmc(), 0L), sousTypeLigne,
                    // Une fiche sans version enregistrée est virtuelle : version 1, brouillon (comme GET /api/fiches-marche/{idDmc}).
                    dmc == null ? null : etatsFiche.getOrDefault(dmc.getIdDmc(), new LigneEligibleDto.EtatFiche("BROUILLON", 1)),
                    dmc == null ? null : etatsDossier.get(dmc.getIdDmc()),
                    dmc == null ? null : dossiersMarche.getOrDefault(dmc.getIdDmc(), List.of()),
                    dmc == null ? null : numeros.getOrDefault(dmc.getIdDmc(), d == null ? null : d.getRefeDossier()),
                    dmc == null ? null : evaluees.contains(dmc.getIdDmc()) ? attributionService.getObject().resume(dmc.getIdDmc()) : List.of()));
        }
        return out;
    }

    /** Un motif d'inéligibilité : code stable et message. */
    public record Motif(String code, String message) {
    }

    /**
     * H4, dans l'ordre : ligne retirée ({@code LIGNE_RETIREE}) ; mode non mappé au type {@code DAO} actif
     * ({@code MODE_NON_DAO}) ; ⚠️ lot 1c : forme de marché non outillée ou absente ({@code FORME_NON_OUTILLEE},
     * si {@code verifierForme} — la liste des éligibles la garde et la montre, {@code formeOutillee = false}) ; plan sans PV signé favorable — avis {@code FAV} au PV signé, ou {@code FAVR} une fois les
     * réserves levées ({@code PV_NON_SIGNE}) ; DMC déjà créé ({@code DAO_EXISTANT}). Le PV pris en compte est le
     * plus récent des PV signés du dossier.
     *
     * @param caches modes, types, PV par dossier et DMC par filiation partagés par une liste d'éligibles
     */
    private Optional<Motif> motifInegibilite(Marche marche, Dossier dossier, Caches caches, boolean verifierForme) {
        if (Boolean.TRUE.equals(marche.getSupprimee())) {
            return Optional.of(new Motif("LIGNE_RETIREE", "La ligne " + marche.getIdDetail() + " a été retirée du plan."));
        }
        if (dossier != null && StatutDossier.REMPLACE.name().equals(dossier.getStatut())) {
            return Optional.of(new Motif("VERSION_DEPASSEE", "Le plan " + dossier.getRefeDossier() + " a été remplacé "
                    + "par une version plus récente : préparer l'appel d'offres depuis la version courante."));
        }
        ModePassation mode = caches.mode(marche.getIdMode());
        TypeDmc type = mode == null || mode.getIdTypeDmc() == null ? null : caches.type(mode.getIdTypeDmc());
        String libelleMode = mode == null ? String.valueOf(marche.getIdMode()) : mode.getLibelle();
        if (type == null) {
            return Optional.of(new Motif("MODE_NON_DAO", "Le mode « " + libelleMode + " » n'est rattaché à aucun type "
                    + "de dossier de mise en concurrence — à faire par l'Administrateur (Types de DMC)."));
        }
        if (!type.isActif() || !TYPE_DAO.equalsIgnoreCase(type.getCode())) {
            return Optional.of(new Motif("MODE_NON_DAO", "Le mode « " + libelleMode + " » n'est pas un appel d'offres "
                    + "(type de DMC « " + type.getCode() + (type.isActif() ? "" : ", inactif") + " »)."));
        }
        if (verifierForme) {
            Optional<Motif> forme = motifForme(marche.formeMarcheSaisie());
            if (forme.isPresent()) {
                return forme;
            }
            Optional<Motif> categorie = categorie(marche).motif();
            if (categorie.isPresent()) {
                return categorie;
            }
        }
        // ⚠️ R6b (manuel révisé, lot 2, §B1 règle 5 ; V107) — un plan réputé approuvé (art. 26-IV) vaut PV signé favorable.
        boolean approuve = dossier != null && (dossier.getReputeApprouveLe() != null
                || caches.pv.computeIfAbsent(dossier.getIdDossier(), id -> valeursPpm.pvSigneFavorable(dossier)));
        if (!approuve) {
            return Optional.of(new Motif("PV_NON_SIGNE", "Le plan " + (dossier == null ? "" : dossier.getRefeDossier() + " ")
                    + "n'a pas de PV signé favorable (ou ses réserves ne sont pas levées) et n'est pas réputé approuvé."));
        }
        if (caches.dejaDao(marche.getIdLigneOrigine())) {
            return Optional.of(new Motif("DAO_EXISTANT", "La ligne de marché " + marche.getIdDetail()
                    + " a déjà un DMC (sur cette version du plan ou une précédente)."));
        }
        return Optional.empty();
    }

    /** Caches d'une évaluation : référentiels lus une fois, DMC par filiation (précalculés pour une liste). */
    private final class Caches {
        private final Map<Integer, ModePassation> modes = new HashMap<>();
        private final Map<Long, TypeDmc> types = new HashMap<>();
        private final Map<Integer, Boolean> pv = new HashMap<>();
        private final Map<Integer, java.util.Optional<cnm.prs.entity.Nature>> natures = new HashMap<>();
        private final Map<Integer, DossierMec> dmcParOrigine;

        Caches(Map<Integer, DossierMec> dmcParOrigine) {
            this.dmcParOrigine = dmcParOrigine;
        }

        ModePassation mode(Integer idMode) {
            return idMode == null ? null : modes.computeIfAbsent(idMode, id -> modeRepository.findById(id).orElse(null));
        }

        TypeDmc type(Long idType) {
            return types.computeIfAbsent(idType, id -> typeDmcRepository.findById(id).orElse(null));
        }

        boolean dejaDao(Integer origine) {
            return dmcParOrigine != null ? dmcParOrigine.containsKey(origine)
                    : !repository.findParFiliation(origine).isEmpty();
        }
    }


    @Transactional(readOnly = true)
    public DmcDto findByMarche(Integer idDetail) {
        DossierMec dmc = repository.findByIdDetail(idDetail)
                .orElseThrow(() -> new ResourceNotFoundException("Aucun DMC pour la ligne de marché : " + idDetail));
        controlerVisibilite(dmc);
        return avecDossierSoumis(DmcMapper.toDto(dmc));
    }

    @Transactional(readOnly = true)
    public DmcDto findById(Long idDmc) {
        DossierMec dmc = repository.findById(idDmc)
                .orElseThrow(() -> new ResourceNotFoundException("DMC introuvable : " + idDmc));
        controlerVisibilite(dmc);
        return avecDossierSoumis(DmcMapper.toDto(dmc));
    }

    /** ⚠️ Lot 1b (2026-09-23, §B1) — le dossier soumis que porte ce DMC ({@code t_dossier.ID_DMC}), nul sinon. */
    private DmcDto avecDossierSoumis(DmcDto dto) {
        dto.setIdDossierSoumis(dossierRepository.findIdDossierByIdDmc(dto.getIdDmc()).orElse(null));
        return dto;
    }

    /**
     * ⚠️ LOT 3a (2026-08-26) — §1/§3.1 : le DMC est rattaché à une ligne de marché, donc à un dossier ;
     * sa lecture suit le périmètre de ce dossier (Président/Admin : tout ; contrôleurs : leur localité ;
     * PRMP : ses dossiers). La <strong>création</strong> est réservée à l'Administrateur, à la PRMP et à son UGPM
     * ({@code @PreAuthorize} sur {@code DmcController}).
     */
    private void controlerVisibilite(DossierMec dmc) {
        perimetre.controler(marcheRepository.findIdDossierByIdDetail(dmc.getIdDetail()).orElse(null));
    }

    /**
     * Re-dérive le type du DMC d'un marché <strong>si</strong> il existe et est encore {@code A_PREPARER}
     * (appelé au changement de mode de passation). Si le nouveau mode n'est pas mappé, le DMC est laissé
     * inchangé (on ne bloque pas la modification du marché).
     */
    public void reAffecterTypeSiApreparer(Integer idDetail) {
        repository.findByIdDetail(idDetail).ifPresent(dmc -> {
            if (dmc.getStatut() != StatutDmc.A_PREPARER) {
                return;
            }
            Marche marche = marcheRepository.findById(idDetail).orElse(null);
            if (marche == null || marche.getIdMode() == null) {
                return;
            }
            ModePassation mode = modeRepository.findById(marche.getIdMode()).orElse(null);
            TypeDmc type = typeDuMode(mode);
            if (type != null && type.isActif()) {
                dmc.setIdTypeDmc(type.getIdTypeDmc());
                dmc.setTypeDmc(type);   // garde l'association en phase avec la colonne (évite une lecture stale)
                repository.save(dmc);
            }
        });
    }

    /** Supprime le DMC d'un marché (cascade applicative à la suppression du marché). */
    public void supprimerPourMarche(Integer idDetail) {
        repository.deleteByIdDetail(idDetail);
    }

    /**
     * ⚠️ 2026-09-27 (statut « Lancé », §B3 / §B4) — le dossier de mise en concurrence <strong>vivant</strong> d'une
     * ligne : celui que porte sa filiation ({@code ID_LIGNE_ORIGINE}, sur cette version du plan ou une précédente),
     * le plus ancien s'il y en avait plusieurs ; {@code null} sans DMC. C'est lui qui interdit le retour à « Prévu » et
     * que {@code MarcheDto.idDmc} sert au front.
     */
    @Transactional(readOnly = true)
    public Long idDmcVivant(Marche ligne) {
        if (ligne == null) {
            return null;
        }
        return repository.findParFiliation(ligne.getIdLigneOrigine()).stream().map(DossierMec::getIdDmc).findFirst().orElse(null);
    }

    /**
     * ⚠️ 2026-09-30 (statut « Lancé » à l'avis, §B3 / §B4) — la date de la première impression de l'avis spécifique de la
     * filiation de la ligne, {@code null} sans avis.
     */
    @Transactional(readOnly = true)
    public java.time.LocalDate avisImprimeLe(Marche ligne) {
        if (ligne == null) {
            return null;
        }
        java.time.LocalDateTime d = documentRepository.premierAvisDeLaFiliation(ligne.getIdLigneOrigine() != null
                ? ligne.getIdLigneOrigine() : ligne.getIdDetail());
        return d == null ? null : d.toLocalDate();
    }

    /** La même date pour chaque filiation ({@code origine → date}), en une requête, pour une liste de lignes. */
    @Transactional(readOnly = true)
    public Map<Integer, java.time.LocalDate> avisParOrigine() {
        Map<Integer, java.time.LocalDate> out = new HashMap<>();
        for (Object[] ligne : documentRepository.premiersAvisParOrigine()) {
            out.put((Integer) ligne[0], ((java.time.LocalDateTime) ligne[1]).toLocalDate());
        }
        return out;
    }

    /** Le DMC vivant de chaque filiation ({@code origine → idDmc}), en une requête, pour une liste de lignes. */
    @Transactional(readOnly = true)
    public Map<Integer, Long> dmcParOrigine() {
        Map<Integer, Long> out = new HashMap<>();
        for (Object[] ligne : repository.findAvecOrigine()) {
            Integer origine = (Integer) ligne[0];
            DossierMec d = (DossierMec) ligne[1];
            out.putIfAbsent(origine, d.getIdDmc());   // le plus ancien d'abord (ordre de la requête)
        }
        return out;
    }

    private TypeDmc resoudreType(Marche marche) {
        ModePassation mode = marche.getIdMode() == null ? null
                : modeRepository.findById(marche.getIdMode()).orElse(null);
        TypeDmc type = typeDuMode(mode);
        if (type == null || !type.isActif()) {
            String mode0 = mode != null ? mode.getLibelle() : String.valueOf(marche.getIdMode());
            throw new BadRequestException("Aucun type de DMC actif n'est mappé au mode de passation « " + mode0
                    + " ». Configurez le mapping en administration (mode de passation → type de DMC).");
        }
        return type;
    }

    /**
     * Type de DMC mappé à un mode de passation, résolu via la <strong>colonne</strong> {@code ID_TYPE_DMC}
     * (et non l'association read-only, non renseignée quand seule la colonne est modifiée). {@code null} si
     * mode absent ou non mappé.
     */
    private TypeDmc typeDuMode(ModePassation mode) {
        if (mode == null || mode.getIdTypeDmc() == null) {
            return null;
        }
        return typeDmcRepository.findById(mode.getIdTypeDmc()).orElse(null);
    }
}
