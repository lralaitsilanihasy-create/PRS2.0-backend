package cnm.prs.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import cnm.prs.service.CeremonieService;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 2, §B4) — le rappel de nuit des parts à vérifier :
 * {@code FICHE_SE_VERIFICATION_PART_JOURS} jours avant la date limite de remise, les membres dont la part n'est pas vérifiée
 * depuis la clôture reçoivent {@code PART_A_VERIFIER} ({@link CeremonieService#rappelerVerifications()}).
 */
@Component
public class CeremonieScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(CeremonieScheduler.class);

    private final CeremonieService service;

    public CeremonieScheduler(CeremonieService service) {
        this.service = service;
    }

    @Scheduled(cron = "${app.ceremonie.cron-rappel:0 45 3 * * *}")
    public void rappels() {
        int n = service.rappelerVerifications();
        if (n > 0) {
            LOG.info("Cérémonie des clés : {} rappel(s) de vérification de part émis.", n);
        }
    }
}
