package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.entity.RemiseConcurrence;

/** ⚠️ 2026-10-16 (lot MS-2) — les calculs de la remise en concurrence : l'écart au prix moyen (art. 23), le résultat par offre (art. 30). */
class RemiseConcurrenceServiceTest {

    @Test
    @DisplayName("L'écart au prix moyen, en pour cent au dixième ; nul sans prix moyen ; au-delà de 30 % : le seuil de l'art. 23")
    void ecart() {
        assertThat(RemiseConcurrenceService.ecart(new BigDecimal("9000000"), new BigDecimal("6000000"))).isEqualByComparingTo("50.0");
        assertThat(RemiseConcurrenceService.ecart(new BigDecimal("5000000"), new BigDecimal("6000000"))).isEqualByComparingTo("-16.7");
        assertThat(RemiseConcurrenceService.ecart(new BigDecimal("7800000"), new BigDecimal("6000000")))
                .isEqualByComparingTo(RemiseConcurrenceService.SEUIL_ECART);
        assertThat(RemiseConcurrenceService.ecart(new BigDecimal("1"), null)).isNull();
    }

    @Test
    @DisplayName("Le résultat d'une offre consultée : en cours, retenue, non retenue, ou la remise infructueuse")
    void resultat() {
        RemiseConcurrence x = new RemiseConcurrence();
        x.setEtat(RemiseConcurrence.EVALUEE);
        assertThat(RemiseConcurrenceService.resultat(x, "A")).isEqualTo("EN_COURS");
        x.setEtat(RemiseConcurrence.ATTRIBUEE);
        x.setIdOffreRetenue("A");
        assertThat(RemiseConcurrenceService.resultat(x, "A")).isEqualTo("RETENU");
        assertThat(RemiseConcurrenceService.resultat(x, "B")).isEqualTo("NON_RETENU");
        x.setEtat(RemiseConcurrence.INFRUCTUEUSE);
        assertThat(RemiseConcurrenceService.resultat(x, "B")).isEqualTo("INFRUCTUEUSE");
    }
}
