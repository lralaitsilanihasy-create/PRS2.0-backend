package cnm.prs.scheduler;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.CompteAuth;
import cnm.prs.entity.Echeance;
import cnm.prs.entity.Ppm;
import cnm.prs.entity.Prmp;
import cnm.prs.enums.TypeActeur;
import cnm.prs.enums.TypeNotification;
import cnm.prs.repository.CompteAuthRepository;
import cnm.prs.repository.EcheanceRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.PpmRepository;
import cnm.prs.repository.PrmpRepository;
import cnm.prs.service.NotificationService;

/**
 * Comportements automatiques planifiés (§3.1, Module 04) :
 * <ul>
 *   <li>alertes de fin de mandat PRMP à J-90 / J-30 / J-7, expiration du compte à J=0 ;</li>
 *   <li>alertes de jalons du calendrier à l'approche de la date prévue.</li>
 * </ul>
 *
 * <p>Les jobs s'exécutent une fois par jour : un palier ({@code joursRestants == 90/30/7})
 * ne se déclenche donc qu'une seule fois. Si le serveur est arrêté le jour pile d'un palier,
 * celui-ci est manqué (pas de rattrapage) — limitation assumée.</p>
 */
@Component
public class AlerteScheduler {

    /** Durée du mandat PRMP (§3.1 : DATE_NOMIN + 3 ans) — repli quand aucun mandat n'est déclaré. */
    private static final int MANDAT_ANNEES = 3;
    /** Paliers d'alerte avant expiration, en jours. */
    private static final Set<Long> PALIERS_MANDAT = Set.of(90L, 30L, 7L);
    /** Fenêtre d'alerte des jalons : J-7. */
    private static final int FENETRE_JALON_JOURS = 7;

    private final PrmpRepository prmpRepository;
    private final CompteAuthRepository compteAuthRepository;
    private final EcheanceRepository echeanceRepository;
    private final MarcheRepository marcheRepository;
    private final PpmRepository ppmRepository;
    private final NotificationService notificationService;
    /** ⚠️ Spec « Mandats PRMP » — source d'autorité de la date de fin de mandat. */
    private final cnm.prs.service.MandatService mandatService;

    public AlerteScheduler(PrmpRepository prmpRepository, CompteAuthRepository compteAuthRepository,
            EcheanceRepository echeanceRepository, MarcheRepository marcheRepository,
            PpmRepository ppmRepository, NotificationService notificationService,
            cnm.prs.service.MandatService mandatService) {
        this.mandatService = mandatService;
        this.prmpRepository = prmpRepository;
        this.compteAuthRepository = compteAuthRepository;
        this.echeanceRepository = echeanceRepository;
        this.marcheRepository = marcheRepository;
        this.ppmRepository = ppmRepository;
        this.notificationService = notificationService;
    }

    /**
     * Fin de mandat PRMP (§3.1). Tous les jours à 06:00 (surchargage via {@code app.alertes.cron-mandat}).
     */
    @Scheduled(cron = "${app.alertes.cron-mandat:0 0 6 * * *}")
    @Transactional
    public void alerterFinMandat() {
        LocalDate today = LocalDate.now();
        for (Prmp prmp : prmpRepository.findAll()) {
            if (prmp.getDateNomin() == null) {
                continue;
            }
            // ⚠️ Spec « Mandats PRMP » — le mandat DÉCLARÉ (t_mandat) fait autorité dès qu'il en existe un :
            // sans cela une PRMP valablement reconduite serait alertée — puis expirée — sur sa première
            // nomination. Repli sur DATE_NOMIN + 3 ans pour les PRMP sans mandat déclaré.
            LocalDate expiration = mandatService.finDeMandatDeclare(prmp.getIdPrmp())
                    .orElseGet(() -> prmp.getDateNomin().plusYears(MANDAT_ANNEES));
            long joursRestants = ChronoUnit.DAYS.between(today, expiration);

            if (PALIERS_MANDAT.contains(joursRestants)) {
                // ⚠️ Audit 2026-08-27 (lot B) — l'alerte partait par e-mail SEUL (destinataireRef null) :
                // invisible de « mes notifications » dès que l'e-mail du compte diffère de t_prmp.EMAIL_PRMP.
                // Portée par la PRMP (ref = ID_PRMP), comme PV_SIGNE.
                notificationService.emettrePrmp(TypeNotification.FIN_MANDAT, prmp.getIdPrmp(),
                        prmp.getEmailPrmp(), null, null, null,
                        "Fin de mandat dans " + joursRestants + " jours",
                        "Votre mandat PRMP expire le " + expiration + " (J-" + joursRestants + ").");
            } else if (joursRestants <= 0) {
                expirerComptesPrmp(prmp.getIdPrmp());
            }
        }
    }

    /**
     * Alertes de jalons du calendrier (§3.1, Module 04). Tous les jours à 06:30
     * (surchargage via {@code app.alertes.cron-jalons}).
     */
    @Scheduled(cron = "${app.alertes.cron-jalons:0 30 6 * * *}")
    @Transactional
    public void alerterJalons() {
        LocalDate today = LocalDate.now();
        LocalDate fin = today.plusDays(FENETRE_JALON_JOURS);
        for (Echeance echeance : echeanceRepository.findJalonsAAlerter(today, fin)) {
            String email = resoudreEmailPrmp(echeance.getIdDetail());
            if (email != null) {
                long jours = ChronoUnit.DAYS.between(today, echeance.getDatePrevue());
                notificationService.emettre(null, TypeNotification.ALERTE_DELAI, null, email,
                        "Jalon " + echeance.getTypeJalon() + " dans " + jours + " jour(s)",
                        "Échéance prévue le " + echeance.getDatePrevue() + " (J-" + jours + ").");
            }
            // Marqué traité même si l'e-mail est introuvable, pour ne pas re-traiter chaque jour.
            echeance.setAlerteEnvoyee(true);
            echeanceRepository.save(echeance);
        }
    }

    /** Désactive le(s) compte(s) PRMP encore actif(s) — « le compte est marqué comme expiré » (§3.1). */
    private void expirerComptesPrmp(String idPrmp) {
        for (CompteAuth compte : compteAuthRepository.findByRefActeurAndTypeActeur(idPrmp, TypeActeur.PRMP.name())) {
            if (Boolean.TRUE.equals(compte.getActif())) {
                compte.setActif(false);
                compteAuthRepository.save(compte);
            }
        }
    }

    /** Résout l'e-mail de la PRMP d'un marché : marché → PPM → PRMP. */
    private String resoudreEmailPrmp(Integer idDetail) {
        return marcheRepository.findById(idDetail)
                .flatMap(marche -> ppmRepository.findById(marche.getIdPpm()))
                .map(Ppm::getIdPrmp)
                .flatMap(prmpRepository::findById)
                .map(Prmp::getEmailPrmp)
                .orElse(null);
    }
}
