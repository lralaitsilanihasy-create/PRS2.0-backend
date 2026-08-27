package cnm.prs;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Horloge de test : instant figé, avançable à la demande. Remplace (via un {@code @TestConfiguration}
 * déclarant un bean {@code Clock} {@code @Primary}) l'horloge système de
 * {@link cnm.prs.config.ClockConfig}, pour piloter « maintenant » sans dépendre de la date de la
 * machine ni faire attendre la suite.
 *
 * <p>Extraite d'{@code AlerteSchedulerIntegrationTest} (lot C) le 2026-08-27 : le lot E en a besoin
 * pour vérifier qu'un verrou de {@link cnm.prs.security.LoginRateLimiter} <em>expire</em>, ce qui
 * demanderait sinon quinze minutes d'attente réelle.</p>
 */
final class HorlogeMutable extends Clock {

    private volatile Instant instant;
    private final ZoneId zone;

    HorlogeMutable(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new HorlogeMutable(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }

    /** Positionne l'horloge au début de la date donnée (paliers d'alerte, échéances). */
    void avancerA(LocalDate date) {
        this.instant = date.atStartOfDay(zone).toInstant();
    }

    /** Avance l'horloge d'une durée (fenêtres glissantes, verrous temporaires). */
    void avancerDe(Duration duree) {
        this.instant = this.instant.plus(duree);
    }
}
