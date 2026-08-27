package cnm.prs.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ⚠️ Audit 2026-08-27 (lot C) — horloge injectable, seul point d'accès à « aujourd'hui » pour le code
 * qui doit rester testable sans dépendre de la date système (ex. {@link cnm.prs.scheduler.AlerteScheduler}).
 * Bean par défaut : horloge système réelle, fuseau par défaut de la JVM — aucun changement de
 * comportement en production. Les tests remplacent ce bean par une horloge maîtrisée.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
