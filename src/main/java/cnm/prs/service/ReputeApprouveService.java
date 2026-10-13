package cnm.prs.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.Controleur;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Prmp;
import cnm.prs.enums.StatutDossier;
import cnm.prs.enums.TypeNotification;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.PrmpRepository;
import cnm.prs.repository.PvExamenRepository;

/**
 * ⚠️ R6b (manuel révisé pour l'application, lot 2, §B1 règle 5 ; arbitrage Q6 du pilote ; V107, 2026-10-13) — <strong>le plan réputé
 * approuvé</strong> (art. 26-IV de la loi n° 2016-055) : un plan de passation (famille {@code DDP}) soumis depuis
 * <strong>dix jours ouvrables</strong> sans PV signé est considéré comme approuvé. Arbitrages au backend du 13/10 : un marqueur daté
 * ({@code REPUTE_APPROUVE_LE}), pas un statut — le circuit continue et un avis tardif de la Commission vaut observation ; tout plan
 * sans PV signé compte, quel que soit son statut dans le circuit ; les jours ouvrables sont les jours ouvrés du lundi au vendredi
 * ({@link JoursOuvres}, sans jours fériés) ; la PRMP du plan et les Présidents de sa localité sont notifiés. Le planificateur passe
 * chaque heure ({@code app.plans.cron-repute-approuve}) ; la création d'un DMC accepte un plan réputé approuvé comme un PV favorable.
 */
@Service
public class ReputeApprouveService {

    private static final Logger log = LoggerFactory.getLogger(ReputeApprouveService.class);
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Dix jours ouvrables (délai légal n° 5). */
    public static final int JOURS_OUVRABLES = DelaisLegaux.par(DelaisLegaux.PLAN_REPUTE_APPROUVE).orElseThrow().valeur();
    /** La famille des plans de passation. */
    static final String FAMILLE_DDP = "DDP";
    /** Les statuts hors circuit : jamais réputés approuvés. */
    static final Set<String> STATUTS_EXCLUS = Set.of(StatutDossier.BROUILLON.name(), StatutDossier.CLOTURE.name(), StatutDossier.RETIRE.name(),
            StatutDossier.REMPLACE.name());

    private final DossierRepository dossiers;
    private final PvExamenRepository pvs;
    private final PrmpRepository prmps;
    private final ControleurDirectory controleurs;
    private final JournalDossierService journal;
    private final NotificationService notifications;
    private final Clock clock;

    public ReputeApprouveService(DossierRepository dossiers, PvExamenRepository pvs, PrmpRepository prmps, ControleurDirectory controleurs,
            JournalDossierService journal, NotificationService notifications, Clock clock) {
        this.dossiers = dossiers;
        this.pvs = pvs;
        this.prmps = prmps;
        this.controleurs = controleurs;
        this.journal = journal;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.plans.cron-repute-approuve:0 20 * * * *}")
    public void planifie() {
        int n = verifier();
        if (n > 0) {
            log.info("[PLAN] plan(s) réputé(s) approuvé(s) : {}", n);
        }
    }

    /** Pose le marqueur sur chaque plan soumis depuis dix jours ouvrables sans PV signé ; une fois par plan. */
    @Transactional
    public int verifier() {
        LocalDate aujourdhui = LocalDate.now(clock);
        int n = 0;
        for (Dossier d : dossiers.findByIdTypeDossierAndReputeApprouveLeIsNullAndDateSoumissionIsNotNull(FAMILLE_DDP)) {
            if (STATUTS_EXCLUS.contains(d.getStatut()) || !echu(d.getDateSoumission(), aujourdhui)
                    || !pvs.findSignesParDossierRows(d.getIdDossier()).isEmpty()) {
                continue;
            }
            LocalDateTime maintenant = LocalDateTime.now(clock);
            d.setReputeApprouveLe(maintenant);
            dossiers.save(d);
            LocalDate echeance = echeance(d.getDateSoumission());
            journal.tracer(d, JournalDossierService.PLAN_REPUTE_APPROUVE, "Plan réputé approuvé (art. 26-IV) : soumis le "
                    + d.getDateSoumission().toLocalDate().format(JOUR) + ", sans PV signé à l'expiration des " + JOURS_OUVRABLES
                    + " jours ouvrables (le " + echeance.format(JOUR) + ") ; un avis rendu après coup vaut observation.");
            notifier(d, echeance);
            n++;
        }
        return n;
    }

    /** Le dernier jour du délai : dix jours ouvrables après le jour de la soumission (non compté). */
    public static LocalDate echeance(LocalDateTime dateSoumission) {
        return dateSoumission == null ? null : JoursOuvres.ajouter(dateSoumission.toLocalDate(), JOURS_OUVRABLES);
    }

    /** Échu : au moins dix jours ouvrables se sont écoulés depuis le jour de la soumission (non compté). */
    public static boolean echu(LocalDateTime dateSoumission, LocalDate aujourdhui) {
        return dateSoumission != null && JoursOuvres.entre(dateSoumission.toLocalDate(), aujourdhui) >= JOURS_OUVRABLES;
    }

    private void notifier(Dossier d, LocalDate echeance) {
        String reference = d.getRefeDossier() == null || d.getRefeDossier().isBlank() ? "n° " + d.getIdDossier() : d.getRefeDossier();
        String titre = "Plan de passation réputé approuvé";
        String corps = "Le plan " + reference + ", soumis le " + d.getDateSoumission().toLocalDate().format(JOUR)
                + ", n'a pas reçu l'avis de la Commission dans les " + JOURS_OUVRABLES + " jours ouvrables (échéance le " + echeance.format(JOUR)
                + ") : il est réputé approuvé (art. 26-IV). Les dossiers de mise en concurrence de ses lignes peuvent être préparés ; "
                + "un avis rendu après coup vaut observation.";
        if (d.getIdPrmp() != null) {
            String email = prmps.findById(d.getIdPrmp()).map(Prmp::getEmailPrmp).orElse(null);
            notifications.emettrePrmp(TypeNotification.PLAN_REPUTE_APPROUVE, d.getIdPrmp(), email, d.getIdDossier(),
                    cnm.prs.enums.TypeObjet.DOSSIER, d.getIdDossier(), titre, corps);
        }
        // Les Présidents de la localité du plan ; un Président sans localité voit tout (annuaire) et est notifié aussi.
        List<Controleur> presidents = controleurs.presidents().stream()
                .filter(c -> c.getIdLocalite() == null || d.getIdLocalite() == null || d.getIdLocalite().equals(c.getIdLocalite())).toList();
        for (Controleur p : presidents) {
            notifications.emettre(d.getIdDossier(), TypeNotification.PLAN_REPUTE_APPROUVE, p.getImControleur(), p.getEmailCont(), titre, corps);
        }
    }
}
