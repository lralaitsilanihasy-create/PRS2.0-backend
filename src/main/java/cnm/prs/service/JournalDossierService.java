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

    /** « NOM Prénoms » d'un contrôleur (convention canonique) ; repli sur le matricule. */
    private String nomControleur(String imControleur) {
        if (imControleur == null) {
            return null;
        }
        return controleurRepository.findById(imControleur).map(c -> {
            String nom = ActeurDirectory.nomCanonique(c.getNomCont(), c.getPrenomsCont());
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
        nommerLesAuteursReels(idDossier, lignes);
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
     * ⚠️ <strong>Le journal nomme l'AUTEUR RÉEL de chaque geste, dans UNE SEULE convention</strong>
     * (signalement du 2026-09-08 sur la création, dossier 00305, puis arbitrages du pilote du même jour).
     *
     * <p><strong>Le constat.</strong> À l'écriture, l'opérateur d'une action est la <strong>PRMP en
     * fonction</strong> ({@code CurrentUser.ref()}), qui pour un agent UGPM est sa PRMP de tutelle. Un
     * brouillon saisi par l'UGPM était donc consigné au nom de la PRMP, en contradiction avec le dossier
     * lui-même ({@code CREE_PAR}). Le pilote a tranché deux fois : la ligne doit porter <em>celui qui a
     * fait le geste</em>, et cela vaut pour <strong>tous</strong> les gestes, pas seulement la création.</p>
     *
     * <p><strong>Une seule convention.</strong> Les noms venaient de trois annuaires qui les assemblaient
     * dans deux ordres : la même personne s'écrivait « Prénoms Nom » sur une ligne et « NOM Prénoms » sur
     * la suivante, dans le même tableau. Tout passe désormais par
     * {@link ActeurDirectory#nomCanonique(String, String)} — le défaut ne venait pas d'un annuaire fautif,
     * mais de l'absence de source unique.</p>
     *
     * <p><strong>Dérivé à la lecture, donc rétroactif</strong>, comme le reste de la fusion : les lignes
     * déjà écrites — y compris celles qui portent un nom dans l'ancien ordre — se corrigent d'elles-mêmes,
     * sans reprise de données et sans rien réécrire en base.</p>
     *
     * <p><strong>Ce qui ne bouge pas</strong> : {@code idPrmpOperateur}. C'est la PRMP sous l'autorité de
     * laquelle l'agent a agi ; y mettre l'UGPM allumerait le marqueur « opérateur ≠ attributaire » du
     * front, qui signale qu'une <em>autre PRMP</em> a agi — un contresens. Seul le nom affiché change de
     * source.</p>
     *
     * <p><strong>Identités et replis.</strong> L'auteur d'une ligne est un <em>login</em> pour une action
     * consignée, un <em>matricule</em> ou un <em>identifiant de PRMP</em> pour un événement dérivé (ou une
     * copie figée) : les trois annuaires sont donc interrogés, chacun une seule fois quel que soit le
     * nombre de lignes. La {@code CREATION} prend d'abord son auteur sur le dossier, qui fait foi même
     * lorsque la ligne est muette. Sans résolution, le nom stocké est conservé : on ne remplace jamais un
     * nom connu par un identifiant brut.</p>
     */
    private void nommerLesAuteursReels(Integer idDossier, List<ActionDossierDto> lignes) {
        if (lignes.isEmpty()) {
            return;
        }
        // La création : CREE_PAR fait foi, y compris sur une ligne écrite avant que l'auteur soit consigné.
        String createur = dossierRepository.findById(idDossier).map(Dossier::getCreePar)
                .filter(s -> s != null && !s.isBlank()).orElse(null);
        if (createur != null) {
            for (ActionDossierDto ligne : lignes) {
                if (CREATION.equals(ligne.getTypeAction())) {
                    ligne.setAuteur(createur);
                }
            }
        }
        java.util.Set<String> identites = lignes.stream().map(ActionDossierDto::getAuteur)
                .filter(s -> s != null && !s.isBlank())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        if (identites.isEmpty()) {
            return;
        }
        java.util.Map<String, String> noms = new java.util.HashMap<>(acteurDirectory.nomsParLogin(identites));
        java.util.Set<String> restantes = identites.stream().filter(i -> !noms.containsKey(i))
                .collect(java.util.stream.Collectors.toSet());
        if (!restantes.isEmpty()) {
            // Événements dérivés et copies figées : l'auteur y est un matricule de contrôleur…
            controleurRepository.findAllById(restantes).forEach(c -> noms.put(c.getImControleur(),
                    ActeurDirectory.nomCanonique(c.getNomCont(), c.getPrenomsCont())));
            // … ou un identifiant de PRMP (demande de retrait).
            prmpRepository.findAllById(restantes).forEach(p -> noms.putIfAbsent(p.getIdPrmp(),
                    ActeurDirectory.nomCanonique(p.getNomPrmp(), p.getPrenomsPrmp())));
        }
        for (ActionDossierDto ligne : lignes) {
            String nom = ligne.getAuteur() == null ? null : noms.get(ligne.getAuteur());
            if (nom != null && !nom.isBlank()) {
                ligne.setNomOperateur(nom);
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
            String nom = ActeurDirectory.nomCanonique(p.getNomPrmp(), p.getPrenomsPrmp());
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
