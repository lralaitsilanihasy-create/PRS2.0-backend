package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.dto.FinanciereDto;

/** ⚠️ PI-d2a (§B5) — le classement selon la méthode, sans base : méthodes, poids, scores, égalités et départage. */
class EvaluationFinanciereClassementTest {

    private static FinanciereDto.Ligne ligne(String id, String t, String compare, String statut) {
        return new FinanciereDto.Ligne(id, "f" + id, Integer.valueOf(id.substring(1)), null, id, new BigDecimal(t), null, true, null, statut, null,
                compare == null ? null : new BigDecimal(compare), null, null, null, false);
    }

    private static Integer rang(List<FinanciereDto.Ligne> l, String id) {
        return l.stream().filter(p -> p.idOffre().equals(id)).findFirst().orElseThrow().rang();
    }

    @Test
    @DisplayName("Les méthodes de B02-MS-01 se reconnaissent à leur libellé")
    void methodes() {
        assertThat(EvaluationFinanciereService.codeMethode("Qualité technique, expérience et proposition financière")).isEqualTo("QUALITE_COUT");
        assertThat(EvaluationFinanciereService.codeMethode("Qualité technique exclusivement")).isEqualTo("QUALITE_TECHNIQUE");
        assertThat(EvaluationFinanciereService.codeMethode("Budget prédéterminé dont le candidat propose la meilleure utilisation")).isEqualTo("BUDGET");
        assertThat(EvaluationFinanciereService.codeMethode("Meilleure proposition financière parmi les candidats ayant la note minimale"))
                .isEqualTo("MOINDRE_COUT");
        assertThat(EvaluationFinanciereService.codeMethode("Qualification du consultant")).isEqualTo("QUALIFICATION");
        assertThat(EvaluationFinanciereService.codeMethode("Autre")).isNull();
    }

    @Test
    @DisplayName("Les poids totalisent 1, ou 100 (ramenés à 1) ; sinon ils sont invalides")
    void poids() {
        assertThat(EvaluationFinanciereService.poids("0,7", "0,3")).containsExactly(new BigDecimal("0.7"), new BigDecimal("0.3"));
        assertThat(EvaluationFinanciereService.poids("80", "20")).containsExactly(new BigDecimal("0.8"), new BigDecimal("0.2"));
        assertThat(EvaluationFinanciereService.poids("0.8", "0.3")).isNull();
        assertThat(EvaluationFinanciereService.poids(null, "0.3")).isNull();
    }

    @Test
    @DisplayName("Moindre coût : le montant comparé le plus bas ; les écartées ne sont pas classées")
    void moindreCout() {
        List<FinanciereDto.Ligne> l = EvaluationFinanciereService.classer("MOINDRE_COUT", null,
                List.of(ligne("o1", "90", "500", "EVALUEE"), ligne("o2", "75", "400", "EVALUEE"), ligne("o3", "80", "300", "ECARTEE")), List.of());
        assertThat(rang(l, "o2")).isEqualTo(1);
        assertThat(rang(l, "o1")).isEqualTo(2);
        assertThat(rang(l, "o3")).isNull();
    }

    @Test
    @DisplayName("Qualité-coût : une égalité de score garde le même rang et se signale, puis le départage la résout")
    void egaliteEtDepartage() {
        BigDecimal[] w = { new BigDecimal("0.8"), new BigDecimal("0.2") };
        // Deux propositions identiques (T 80, F 1000) : S = 80 × 0,8 + 100 × 0,2 = 84 pour chacune ; o3 derrière.
        List<FinanciereDto.Ligne> base = List.of(ligne("o1", "80", "1000", "EVALUEE"), ligne("o2", "80", "1000", "EVALUEE"),
                ligne("o3", "70", "2000", "EVALUEE"));
        List<FinanciereDto.Ligne> l = EvaluationFinanciereService.classer("QUALITE_COUT", w, base, List.of());
        assertThat(rang(l, "o1")).isEqualTo(1);
        assertThat(rang(l, "o2")).isEqualTo(1);
        assertThat(rang(l, "o3")).isEqualTo(3);
        assertThat(l.stream().filter(FinanciereDto.Ligne::egalite).map(FinanciereDto.Ligne::idOffre)).containsExactlyInAnyOrder("o1", "o2");
        assertThat(l.get(2).scoreCombine()).isEqualByComparingTo("66");   // 70 × 0,8 + 50 × 0,2
        List<FinanciereDto.Ligne> d = EvaluationFinanciereService.classer("QUALITE_COUT", w, base, List.of("o2", "o1"));
        assertThat(rang(d, "o2")).isEqualTo(1);
        assertThat(rang(d, "o1")).isEqualTo(2);
        assertThat(d.stream().noneMatch(FinanciereDto.Ligne::egalite)).isTrue();
    }

    @Test
    @DisplayName("Qualité technique exclusivement : la note technique, enveloppe ouverte ou non")
    void qualiteTechnique() {
        List<FinanciereDto.Ligne> l = EvaluationFinanciereService.classer("QUALITE_TECHNIQUE", null,
                List.of(ligne("o1", "75", null, "NON_OUVERTE"), ligne("o2", "88", "900", "EVALUEE")), List.of());
        assertThat(rang(l, "o2")).isEqualTo(1);
        assertThat(rang(l, "o1")).isEqualTo(2);
    }
}
