package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.exception.BadRequestException;

/** ⚠️ R10 (manuel révisé pour l'application, lot 2, §B5 ; H2) — la signature des offres et l'original de l'acte d'engagement. */
class ActeEngagementOriginalR10Test {

    @Test
    @DisplayName("SIGNATURE_OFFRE : vide = défaut, casse libre, valeur inconnue refusée")
    void signatureOffre() {
        assertThat(ParametresInternesService.lireSignatureOffre(null)).isNull();
        assertThat(ParametresInternesService.lireSignatureOffre("  ")).isNull();
        assertThat(ParametresInternesService.lireSignatureOffre(" certifiee ")).isEqualTo("CERTIFIEE");
        assertThat(ParametresInternesService.signatureEffective(null)).isEqualTo(ParametresInternesService.SCELLE_PLUS_ORIGINAL);
        BadRequestException e = catchThrowableOfType(BadRequestException.class, () -> ParametresInternesService.lireSignatureOffre("MANUSCRITE"));
        assertThat(e.getCode()).isEqualTo("SIGNATURE_OFFRE_INVALIDE");
    }

    @Test
    @DisplayName("Projet de marché : l'original annoncé en ligne, daté une fois remis, rien en papier")
    void mentionDuProjet() {
        assertThat(ProjetMarcheService.originalAe(entree(false, null))).isEmpty();
        assertThat(ProjetMarcheService.originalAe(entree(true, null))).contains("original signé à remettre avant la signature du marché");
        assertThat(ProjetMarcheService.originalAe(entree(true, LocalDate.of(2026, 10, 14)))).contains("original signé remis le 14/10/2026");
    }

    private static ProjetMarcheService.Entree entree(boolean exige, LocalDate remis) {
        return new ProjetMarcheService.Entree(1L, 1, null, null, java.util.Map.of(), false, false, null, null, exige, remis);
    }
}
