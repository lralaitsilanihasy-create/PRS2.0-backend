package cnm.prs.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import cnm.prs.service.AttributionService;

/**
 * ⚠️ 2026-10-08 (demande front « attribution », lot 2, tranche 2d-1, §B7) — toutes les heures : les alertes de la PRMP, une fois
 * chacune — délai d'attente écoulé, avis d'attribution à publier sous 5 jours, réexamen à 2 jours de son échéance
 * ({@link AttributionService#alerter()}).
 */
@Component
public class AttributionScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(AttributionScheduler.class);

    private final AttributionService service;

    public AttributionScheduler(AttributionService service) {
        this.service = service;
    }

    @Scheduled(cron = "${app.attribution.cron-alertes:0 15 * * * *}")
    public void alerter() {
        int n = service.alerter();
        if (n > 0) {
            LOG.info("Attribution : {} alerte(s) émise(s) à la PRMP.", n);
        }
    }
}
