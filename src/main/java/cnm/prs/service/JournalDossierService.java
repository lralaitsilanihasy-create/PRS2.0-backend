package cnm.prs.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.ActionDossierDto;
import cnm.prs.entity.ActionDossier;
import cnm.prs.entity.Dossier;
import cnm.prs.repository.ActionDossierRepository;
import cnm.prs.repository.PrmpRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ Règle ajoutée (spec « Mandats PRMP ») — écriture et lecture du <strong>journal des actions</strong>
 * d'un dossier ({@code t_action_dossier}).
 *
 * <p>Chaque action de traitement y est consignée avec l'<strong>opérateur courant</strong> : la PRMP en
 * fonction à la date de l'action et le mandat sous lequel elle agit. L'attribution du dossier, elle,
 * ne bouge pas — c'est précisément la séparation que ce journal rend visible.</p>
 */
@Service
@Transactional(readOnly = true)
public class JournalDossierService {

    /** Types d'action consignés — un vocabulaire fermé, pour que le front puisse les libeller. */
    public static final String CREATION = "CREATION";
    public static final String SOUMISSION = "SOUMISSION";
    public static final String RESOUMISSION = "RESOUMISSION";
    public static final String TRANSMISSION_COMPLEMENTS = "TRANSMISSION_COMPLEMENTS";
    public static final String TRANSMISSION_COMPLEMENTS_DEPOT = "TRANSMISSION_COMPLEMENTS_DEPOT";
    public static final String SUPPRESSION = "SUPPRESSION";
    public static final String MISE_A_JOUR = "MISE_A_JOUR";

    /**
     * ⚠️ Gestes du <strong>circuit de dispatch</strong> (règle du pilote, 2026-09-04). Le chronométrage
     * journalise les ÉTAPES et leurs durées, mais le dispatch ne garde que son <em>dernier</em> état :
     * une réattribution écrase l'attributaire, un retrait supprime la ligne. Sans ces traces, l'histoire
     * du dossier — à qui il est passé, combien de fois — est irrécupérable.
     */
    public static final String DISPATCH = "DISPATCH";
    /** Changement d'attributaire au profit d'un tiers. */
    public static final String REATTRIBUTION = "REATTRIBUTION";
    /** Changement d'attributaire au profit de l'appelant lui-même (le CC reprend le dossier). */
    public static final String REPRISE = "REPRISE";
    /** Retrait du dispatch : retour du dossier en pré-dispatch, aval purgé. */
    public static final String RETRAIT_DISPATCH = "RETRAIT_DISPATCH";
    /** Réception enregistrée COMPLET : le dossier devient prêt à dispatcher. */
    public static final String RECEPTION = "RECEPTION";

    private final ActionDossierRepository repository;
    private final PrmpRepository prmpRepository;
    private final MandatService mandatService;
    /** ⚠️ 2026-09-04 — résolution du nom pour les gestes de circuit posés par un contrôleur. */
    private final cnm.prs.repository.ControleurRepository controleurRepository;
    /** ⚠️ 2026-09-04 — les événements de traitement, dérivés à la lecture. */
    private final JournalTraitementService traitement;
    /** ⚠️ 2026-09-08 — la CRÉATION revient à son auteur réel : le dossier porte le login créateur. */
    private final cnm.prs.repository.DossierRepository dossierRepository;
    /** ⚠️ 2026-09-08 — login → nom lisible, quel que soit le type d'acteur (PRMP, UGPM, contrôleur). */
    private final ActeurDirectory acteurDirectory;

    public JournalDossierService(ActionDossierRepository repository, PrmpRepository prmpRepository,
            MandatService mandatService, cnm.prs.repository.ControleurRepository controleurRepository,
            JournalTraitementService traitement, cnm.prs.repository.DossierRepository dossierRepository,
            ActeurDirectory acteurDirectory) {
        this.dossierRepository = dossierRepository;
        this.acteurDirectory = acteurDirectory;
        this.controleurRepository = controleurRepository;
        this.traitement = traitement;
        this.repository = repository;
        this.prmpRepository = prmpRepository;
        this.mandatService = mandatService;
    }

    /**
     * Consigne une action sur un dossier. L'opérateur est lu sur le jeton courant ({@code ref} = PRMP,
     * ou sa PRMP de tutelle pour un agent UGPM) ; l'auteur réel reste le login.
     *
     * <p>L'écriture rejoint la transaction de l'action qu'elle décrit — délibérément : un journal qui
     * survivrait au rollback de son action raconterait un événement qui n'a pas eu lieu.</p>
     */
    @Transactional
    public void tracer(Integer idDossier, String typeAction, String detail) {
        String operateur = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
        ActionDossier action = new ActionDossier();
        action.setIdDossier(idDossier);
        action.setDateAction(LocalDateTime.now());
        action.setTypeAction(typeAction);
        action.setIdPrmpOperateur(operateur);
        action.setNomOperateur(nomOperateur(operateur));
        action.setAuteur(CurrentUser.login().orElse(operateur));
        action.setIdMandatOperateur(operateur == null ? null : mandatService.idMandatCourant(operateur));
        action.setDetail(tronquer(detail, 500));
        repository.save(action);
    }

    /** Variante prenant le dossier, pour les appels qui l'ont déjà chargé. */
    @Transactional
    public void tracer(Dossier dossier, String typeAction, String detail) {
        tracer(dossier.getIdDossier(), typeAction, detail);
    }

    /**
     * ⚠️ Variante <strong>CONTRÔLEUR</strong> (2026-09-04) — consigne un geste du circuit posé par un
     * agent de la CNM (Président, Chef de commission…), et non par une PRMP.
     *
     * <p>{@code idPrmpOperateur} et {@code idMandatOperateur} restent <strong>nuls</strong> : ce sont des
     * concepts PRMP. Les renseigner avec un matricule de contrôleur allumerait le marqueur « opérateur ≠
     * attributaire » du front, qui signale qu'une PRMP <em>autre</em> que la propriétaire a agi — un
     * contresens ici. Seul le <strong>nom</strong> est résolu, depuis l'annuaire des contrôleurs.</p>
     */
    @Transactional
    public void tracerControleur(Integer idDossier, String typeAction, String detail) {
        String im = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
        ActionDossier action = new ActionDossier();
        action.setIdDossier(idDossier);
        action.setDateAction(LocalDateTime.now());
        action.setTypeAction(typeAction);
        action.setIdPrmpOperateur(null);
        action.setIdMandatOperateur(null);
        action.setNomOperateur(nomControleur(im));
        action.setAuteur(CurrentUser.login().orElse(im));
        action.setDetail(tronquer(detail, 500));
        repository.save(action);
    }

    /** « Prénoms Nom » d'un contrôleur ; repli sur le matricule, {@code null} si l'acteur est inconnu. */
    private String nomControleur(String imControleur) {
        if (imControleur == null) {
            return null;
        }
        return controleurRepository.findById(imControleur).map(c -> {
            String nom = ((c.getPrenomsCont() == null ? "" : c.getPrenomsCont()) + " "
                    + (c.getNomCont() == null ? "" : c.getNomCont())).trim();
            return nom.isBlank() ? imControleur : nom;
        }).orElse(imControleur);
    }
    /** Journal d'un dossier, chronologique. Le contrôle de visibilité est fait par l'appelant. */
    public List<ActionDossierDto> journal(Integer idDossier) {
        // ⚠️ Journal COMPLET (règle du pilote, 2026-09-04) — les actions stockées, plus les événements
        // de traitement DÉRIVÉS des données (navettes, dates du PV, vérifications, SIGMP). Le journal
        // s'arrêtait à la réattribution alors que le chronométrage allait jusqu'à la co-signature.
        // Dérivés et non écrits : les dossiers DÉJÀ traités deviennent complets d'office, ce qu'une
        // écriture au fil de l'eau n'aurait jamais rattrapé.
        List<ActionDossierDto> lignes = new java.util.ArrayList<>(
                repository.findByIdDossierOrderByDateActionAscIdActionAsc(idDossier).stream()
                        .map(JournalDossierService::toDto).toList());
        lignes.addAll(traitement.evenements(idDossier));
        attribuerLaCreationAuCreateur(idDossier, lignes);
        // Ordre chronologique STRICT ; à instant égal, le rang du circuit tranche. Sans lui, les actes
        // de fin de parcours — datés sans heure — se seraient rangés dans un ordre arbitraire.
        lignes.sort(java.util.Comparator
                .comparing(ActionDossierDto::getDateAction,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                .thenComparingInt(a -> JournalTraitementService.rang(a.getTypeAction()))
                .thenComparing(ActionDossierDto::getIdAction,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
        return lignes;
    }

    /**
     * ⚠️ <strong>Signalement pilote (2026-09-08, dossier 00305)</strong> — la ligne {@code CREATION} revient
     * à son <strong>auteur réel</strong>, et non à la PRMP de tutelle.
     *
     * <p>À l'écriture, l'opérateur d'une action est la <strong>PRMP en fonction</strong> ({@code
     * CurrentUser.ref()}), qui pour un agent UGPM est sa PRMP de tutelle : c'est ce qui donne son sens au
     * couple opérateur/mandat, et il ne change pas. Mais la <em>création</em> d'un dossier n'est pas un acte
     * de traitement sous mandat : c'est une saisie, et le dossier sait qui l'a faite — {@code CREE_PAR}
     * porte le login créateur, celui-là même que {@code DossierDto.creePar} expose déjà correctement. Le
     * journal disait donc « la PRMP » d'un brouillon saisi par l'UGPM, en contradiction avec le dossier.</p>
     *
     * <p><strong>Dérivé à la lecture, donc rétroactif</strong> — comme le reste de la fusion : les dossiers
     * déjà créés se corrigent d'eux-mêmes, sans reprise de données. Seul le <strong>nom</strong> (et
     * l'auteur) bouge : {@code idPrmpOperateur} reste la PRMP de tutelle, qui est bien celle sous
     * l'autorité de laquelle l'UGPM a saisi. Y mettre l'UGPM allumerait le marqueur « opérateur ≠
     * attributaire » du front, qui signale qu'une <em>autre PRMP</em> a agi — un contresens ici.</p>
     *
     * <p>Replis, dans l'ordre : le {@code CREE_PAR} du dossier, puis le login consigné sur la ligne
     * elle-même (un dossier d'avant {@code CREE_PAR}), puis le nom stocké — on ne remplace jamais un nom
     * connu par un login brut.</p>
     */
    private void attribuerLaCreationAuCreateur(Integer idDossier, List<ActionDossierDto> lignes) {
        List<ActionDossierDto> creations = lignes.stream()
                .filter(l -> CREATION.equals(l.getTypeAction())).toList();
        if (creations.isEmpty()) {
            return;
        }
        String createur = dossierRepository.findById(idDossier).map(Dossier::getCreePar)
                .filter(s -> s != null && !s.isBlank())
                .orElseGet(() -> creations.stream().map(ActionDossierDto::getAuteur)
                        .filter(s -> s != null && !s.isBlank()).findFirst().orElse(null));
        if (createur == null) {
            return;
        }
        String nom = acteurDirectory.nomsParLogin(java.util.Set.of(createur)).get(createur);
        for (ActionDossierDto creation : creations) {
            creation.setAuteur(createur);
            if (nom != null && !nom.isBlank()) {
                creation.setNomOperateur(nom);
            }
        }
    }

    /**
     * ⚠️ Signalement pilote (2026-09-07, dossier 00001) — <strong>fige</strong> les événements de traitement
     * dérivés (soumission d'examen, retours, transmission, visa, signatures, vérifications, SIGMP,
     * archivage) en lignes de {@code t_action_dossier}, <strong>juste avant</strong> que la purge du circuit
     * n'efface leurs sources (annulation d'un dispatch, retrait accepté). Sans cela, le journal — dérivé à la
     * lecture depuis les navettes et les PV — oubliait que le dossier était passé par l'examen, alors que le
     * chronométrage, lui, gardait ses tâches.
     *
     * <p>Arbitrage (troisième voie entre « persister au fil de l'eau » et « documenter la perte ») : la
     * dérivation à la lecture garde son intérêt — les dossiers déjà traités sont complets d'office, et rien
     * n'est écrit en double tant que les sources vivent. On n'écrit qu'au moment où elles vont disparaître :
     * une fois, et exactement ce que le journal montrait la seconde d'avant. Les copies portent les mêmes
     * types que les événements dérivés, donc la même visibilité hiérarchique au front. Idempotent par
     * construction : après la purge, plus rien n'est dérivable, donc plus rien à figer.</p>
     */
    @Transactional
    public void figerTraitement(Integer idDossier) {
        if (idDossier == null) {
            return;
        }
        List<ActionDossierDto> evenements = traitement.evenements(idDossier);
        for (ActionDossierDto e : evenements) {
            ActionDossier action = new ActionDossier();
            action.setIdDossier(idDossier);
            action.setDateAction(e.getDateAction() == null ? LocalDateTime.now() : e.getDateAction());
            action.setTypeAction(e.getTypeAction());
            action.setIdPrmpOperateur(null);
            action.setIdMandatOperateur(null);
            action.setNomOperateur(e.getNomOperateur());
            action.setAuteur(e.getAuteur());
            action.setDetail(tronquer(e.getDetail(), 500));
            repository.save(action);
        }
    }

    /** Supprime le journal d'un dossier (cascade de la suppression d'un brouillon). */
    @Transactional
    public void purger(Integer idDossier) {
        repository.deleteByIdDossier(idDossier);
    }

    public String nomOperateur(String idPrmp) {
        if (idPrmp == null) {
            return null;
        }
        return prmpRepository.findById(idPrmp).map(p -> {
            String nom = ((p.getPrenomsPrmp() == null ? "" : p.getPrenomsPrmp()) + " "
                    + (p.getNomPrmp() == null ? "" : p.getNomPrmp())).trim();
            return nom.isBlank() ? idPrmp : nom;
        }).orElse(idPrmp);
    }

    private static String tronquer(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private static ActionDossierDto toDto(ActionDossier entity) {
        ActionDossierDto dto = new ActionDossierDto();
        dto.setIdAction(entity.getIdAction());
        dto.setIdDossier(entity.getIdDossier());
        dto.setDateAction(entity.getDateAction());
        dto.setTypeAction(entity.getTypeAction());
        dto.setIdPrmpOperateur(entity.getIdPrmpOperateur());
        dto.setNomOperateur(entity.getNomOperateur());
        dto.setAuteur(entity.getAuteur());
        dto.setIdMandatOperateur(entity.getIdMandatOperateur());
        dto.setDetail(entity.getDetail());
        return dto;
    }
}
