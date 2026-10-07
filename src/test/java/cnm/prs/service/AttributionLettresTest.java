package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.dto.EvaluationDto;

/**
 * ⚠️ 2026-10-07 (attribution, tranche 2b ; constats C2 et D2 de la recette) — le délai d'exécution lu avec son unité, et les motifs du
 * rejet imprimés sur la lettre au candidat non retenu.
 */
class AttributionLettresTest {

    @Test
    @DisplayName("C2/D2 : le délai porte son unité (« 6 mois ») ; sans unité, le nombre seul ; sans délai, nul")
    void delaiAvecUnite() {
        Map<String, Object> ae = new HashMap<>();
        assertThat(EvaluationService.delaiLu(ae)).isNull();
        ae.put("delai", 6);
        assertThat(EvaluationService.delaiLu(ae)).isEqualTo("6");
        ae.put("delaiUnite", "MOIS");
        assertThat(EvaluationService.delaiLu(ae)).isEqualTo("6 mois");
        ae.put("delaiUnite", "JOURS");
        assertThat(EvaluationService.delaiLu(ae)).isEqualTo("6 jours");
        assertThat(EvaluationService.delaiLu(null)).isNull();
    }

    @Test
    @DisplayName("Motifs du rejet : l'étape qui a écarté l'offre, sa qualification, son motif et sa clause ; à défaut, son rang")
    void motifsDuRejet() {
        EvaluationDto.OffreEvaluee ecartee = offre(new EvaluationDto.Ecartement("CONFORMITE", "NON_CONFORME", "Garantie absente", "IC 19.1",
                null, null, null), 2);
        assertThat(AttributionService.motifRejet(ecartee))
                .isEqualTo("Offre écartée à l'examen préliminaire (non conforme) : Garantie absente (IC 19.1).");
        assertThat(AttributionService.motifRejet(offre(null, 2))).startsWith("Offre conforme, classée au rang 2 des offres évaluées");
    }

    private static EvaluationDto.OffreEvaluee offre(EvaluationDto.Ecartement e, Integer rang) {
        return new EvaluationDto.OffreEvaluee("x", 1, new EvaluationDto.Entreprise("1", "A"), null, null, null, null, rang, false, e, 0, null);
    }
}
