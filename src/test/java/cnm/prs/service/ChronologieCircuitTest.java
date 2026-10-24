package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.exception.BadRequestException;

/** ⚠️ 2026-10-24 (demande front « heure-serveur », H2) — la chronologie du circuit CNM. */
class ChronologieCircuitTest {

    private static final LocalDateTime SOUMISSION = LocalDateTime.of(2026, 10, 20, 10, 0, 35);

    @Test
    @DisplayName("Antérieure à la minute : 09:59 refusée, 10:00 admise malgré les secondes de la soumission")
    void anterieure() {
        assertThatThrownBy(() -> ChronologieCircuit.exiger("RECEPTION", SOUMISSION.withMinute(0).minusMinutes(1), "SOUMISSION", SOUMISSION))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> {
                    BadRequestException b = (BadRequestException) e;
                    assertThat(b.getCode()).isEqualTo("DATE_ANTERIEURE");
                    assertThat(b.getDetails()).containsEntry("datePrecedente", "2026-10-20T10:00")
                            .containsEntry("etapePrecedente", "SOUMISSION");
                });
        assertThatCode(() -> ChronologieCircuit.exiger("RECEPTION", SOUMISSION.withSecond(0), "SOUMISSION", SOUMISSION))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Future : au-delà de la marge refusée, dans la marge admise ; sans date ou sans étape précédente, rien")
    void future() {
        LocalDateTime maintenant = LocalDateTime.now();
        assertThatThrownBy(() -> ChronologieCircuit.exiger("DISPATCH", maintenant.plusMinutes(ChronologieCircuit.MARGE_FUTUR_MINUTES + 2),
                "RECEPTION", null)).isInstanceOf(BadRequestException.class)
                .extracting(e -> ((BadRequestException) e).getCode()).isEqualTo("DATE_FUTURE");
        assertThatCode(() -> ChronologieCircuit.exiger("DISPATCH", maintenant.plusMinutes(2), "RECEPTION", null))
                .doesNotThrowAnyException();
        assertThatCode(() -> ChronologieCircuit.exiger("DISPATCH", (LocalDateTime) null, "RECEPTION", SOUMISSION))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("L'examen au jour : le jour du dispatch admis, la veille refusée, demain refusé")
    void auJour() {
        LocalDateTime dispatch = LocalDateTime.of(2026, 10, 21, 16, 30);
        assertThatCode(() -> ChronologieCircuit.exiger("EXAMEN", LocalDate.of(2026, 10, 21), "DISPATCH", dispatch))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> ChronologieCircuit.exiger("EXAMEN", LocalDate.of(2026, 10, 20), "DISPATCH", dispatch))
                .extracting(e -> ((BadRequestException) e).getCode()).isEqualTo("DATE_ANTERIEURE");
        assertThatThrownBy(() -> ChronologieCircuit.exiger("EXAMEN", LocalDate.now().plusDays(1), "DISPATCH", null))
                .extracting(e -> ((BadRequestException) e).getCode()).isEqualTo("DATE_FUTURE");
    }

    @Test
    @DisplayName("Date changée : une date seule compare le jour, une date-heure la minute, une saisie vide ne change rien")
    void changee() {
        LocalDateTime enregistree = LocalDateTime.of(2026, 10, 21, 9, 15, 42);
        assertThat(ChronologieCircuit.changee("2026-10-21", LocalDateTime.of(2026, 10, 21, 17, 3), enregistree)).isFalse();
        assertThat(ChronologieCircuit.changee("2026-10-22", LocalDateTime.of(2026, 10, 22, 17, 3), enregistree)).isTrue();
        assertThat(ChronologieCircuit.changee("2026-10-21 09:15", LocalDateTime.of(2026, 10, 21, 9, 15), enregistree)).isFalse();
        assertThat(ChronologieCircuit.changee("2026-10-21 09:16", LocalDateTime.of(2026, 10, 21, 9, 16), enregistree)).isTrue();
        assertThat(ChronologieCircuit.changee(" ", null, enregistree)).isFalse();
        assertThat(ChronologieCircuit.changee("2026-10-21", LocalDateTime.of(2026, 10, 21, 9, 0), null)).isTrue();
    }
}
