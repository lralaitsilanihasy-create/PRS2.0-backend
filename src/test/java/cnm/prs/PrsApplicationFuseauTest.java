package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DateTimeException;
import java.util.TimeZone;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ⚠️ 2026-10-24 (« heure-serveur », fuseau signalé) — le fuseau de la JVM est imposé au démarrage. */
class PrsApplicationFuseauTest {

    private TimeZone avant;

    @BeforeEach
    void garder() {
        avant = TimeZone.getDefault();
    }

    @AfterEach
    void rendre() {
        System.clearProperty("app.fuseau");
        TimeZone.setDefault(avant);
    }

    @Test
    @DisplayName("Sans réglage : Indian/Antananarivo (si APP_FUSEAU n'est pas posée) ; -Dapp.fuseau l'emporte")
    void fuseau() {
        if (System.getenv("APP_FUSEAU") == null) {
            assertThat(PrsApplication.fixerFuseau().getId()).isEqualTo("Indian/Antananarivo");
            assertThat(TimeZone.getDefault().getID()).isEqualTo("Indian/Antananarivo");
        }
        System.setProperty("app.fuseau", "Europe/Paris");
        assertThat(PrsApplication.fixerFuseau().getId()).isEqualTo("Europe/Paris");
        assertThat(TimeZone.getDefault().getID()).isEqualTo("Europe/Paris");
    }

    @Test
    @DisplayName("Un fuseau inconnu arrête le démarrage")
    void inconnu() {
        System.setProperty("app.fuseau", "Mars/Olympus");
        assertThatThrownBy(PrsApplication::fixerFuseau).isInstanceOf(DateTimeException.class);
    }
}
