package cnm.prs.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import cnm.prs.service.OffreService;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 3, §B4, §B6) — toutes les cinq minutes : la purge des dépôts
 * abandonnés et la notification de clôture des dépôts à la date limite ({@link OffreService#entretenir()}).
 */
@Component
public class OffreScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(OffreScheduler.class);

    private final OffreService service;

    public OffreScheduler(OffreService service) {
        this.service = service;
    }

    @Scheduled(cron = "${app.offres.cron-entretien:0 */5 * * * *}")
    public void entretenir() {
        int[] r = service.entretenir();
        if (r[0] > 0 || r[1] > 0) {
            LOG.info("Offres en ligne : {} dépôt(s) abandonné(s) purgé(s), {} clôture(s) de dépôts notifiée(s).", r[0], r[1]);
        }
    }
}
