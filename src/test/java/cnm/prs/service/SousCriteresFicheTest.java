package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.dto.SousCritereDto;

/** ⚠️ 2026-10-08 (lot 3 PI, PI-a, Q3) — les jetons d'impression et les sommes des sous-critères techniques. */
class SousCriteresFicheTest {

    @Test
    @DisplayName("Jetons SOUSCRITERES.<critère> : « a) libellé : n points », une ligne par sous-critère ; sommes par critère")
    void jetonsEtSommes() {
        List<SousCritereDto> l = List.of(new SousCritereDto(1, "B06-TP-04", "Chef de mission", new BigDecimal("20")),
                new SousCritereDto(2, "B06-TP-04", "Expert", new BigDecimal("7.5")),
                new SousCritereDto(3, "B06-TP-03", "Plan de travail", new BigDecimal("10")));
        assertThat(SousCriteresFiche.jetons(l)).containsEntry("SOUSCRITERES.B06-TP-04", "a) Chef de mission : 20 points\nb) Expert : 7.5 points")
                .containsEntry("SOUSCRITERES.B06-TP-03", "a) Plan de travail : 10 points").doesNotContainKey("SOUSCRITERES.B06-TP-02");
        assertThat(SousCriteresFiche.sommes(l).get("B06-TP-04")).isEqualByComparingTo("27.5");
    }
}
