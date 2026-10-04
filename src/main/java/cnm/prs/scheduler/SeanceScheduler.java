package cnm.prs.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import cnm.prs.service.SeanceService;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 4, §B7) — toutes les cinq minutes : les rappels {@code SEANCE_A_VENIR}
 * (la veille, une heure avant l'ouverture des plis) aux membres de la CAO et au responsable ({@link SeanceService#rappeler()}).
 */
@Component
public class SeanceScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(SeanceScheduler.class);

    private final SeanceService service;

    public SeanceScheduler(SeanceService service) {
        this.service = service;
    }

    @Scheduled(cron = "${app.seance.cron-rappel:30 */5 * * * *}")
    public void rappeler() {
        int n = service.rappeler();
        if (n > 0) {
            LOG.info("Séances d'ouverture : {} rappel(s) SEANCE_A_VENIR émis.", n);
        }
    }
}
