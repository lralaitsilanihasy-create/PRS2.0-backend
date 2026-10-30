package cnm.prs.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import cnm.prs.service.AttributionService;

/**
 * ⚠️ 2026-10-08 (demande front « attribution », lot 2, tranche 2d-1, §B7) — toutes les heures : les alertes de la PRMP, une fois
 * chacune — délai d'attente écoulé, avis d'attribution à publier sous 5 jours, réexamen à 2 jours de son échéance
 * ({@link AttributionService#alerter()}). ⚠️ 2026-10-29 (« contrat-cadre-4a », §B2, §B3) — et les rappels des contrats en vigueur
 * ({@link cnm.prs.service.ContratVigueurService#rappeler()}).
 */
@Component
public class AttributionScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(AttributionScheduler.class);

    private final AttributionService service;
    private final cnm.prs.service.SansSuiteService sansSuite;
    private final cnm.prs.service.ContratVigueurService contrats;
    private final cnm.prs.service.ReceptionCommandeService receptions;

    public AttributionScheduler(AttributionService service, cnm.prs.service.SansSuiteService sansSuite,
            cnm.prs.service.ContratVigueurService contrats, cnm.prs.service.ReceptionCommandeService receptions) {
        this.sansSuite = sansSuite;
        this.contrats = contrats;
        this.receptions = receptions;
        this.service = service;
    }

    @Scheduled(cron = "${app.attribution.cron-alertes:0 15 * * * *}")
    public void alerter() {
        int n = service.alerter() + sansSuite.suivre();   // ⚠️ 2d-3 : avis constaté, échéance des 5 jours
        n += contrats.rappeler();   // ⚠️ 2026-10-29 (« contrat-cadre-4a ») : reconduction à décider, remise à lancer, contrat échu
        n += receptions.rappeler();   // ⚠️ 2026-10-30 (« ccag-penalites-reception », §B5) : décision de la commission de réception
        if (n > 0) {
            LOG.info("Attribution : {} alerte(s) émise(s) à la PRMP.", n);
        }
    }
}
