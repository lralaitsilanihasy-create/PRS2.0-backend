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

    public JournalDossierService(ActionDossierRepository repository, PrmpRepository prmpRepository,
            MandatService mandatService, cnm.prs.repository.ControleurRepository controleurRepository,
            JournalTraitementService traitement) {
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
