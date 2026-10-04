package cnm.prs.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import cnm.prs.service.CandidatService;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 1a, §B7) — le ménage quotidien des comptes candidats :
 * suppression des comptes jamais confirmés, archivage des comptes inactifs ({@link CandidatService#menage()}).
 */
@Component
public class CandidatScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(CandidatScheduler.class);

    private final CandidatService service;

    public CandidatScheduler(CandidatService service) {
        this.service = service;
    }

    @Scheduled(cron = "${app.candidats.cron-menage:0 30 3 * * *}")
    public void menage() {
        int[] r = service.menage();
        if (r[0] > 0 || r[1] > 0) {
            LOG.info("Ménage des comptes candidats : {} supprimé(s) (jamais confirmés), {} archivé(s) (inactifs).", r[0], r[1]);
        }
    }
}
