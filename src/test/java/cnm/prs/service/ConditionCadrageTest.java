package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * ⚠️ Fiche marché DAO (demande front du 2026-09-22, §B1) — l'évaluateur de condition, jumeau de celui du front
 * ({@code fiche-marche-modele.ts}, {@code evaluerCondition}) : les cas de recette de la demande, à l'identique.
 */
class ConditionCadrageTest {

    private static final Map<String, Object> CADRAGE = Map.of("garantieSoumission", "OUI", "provenance", "IMPORTEES",
            "typePrix", "UNITAIRES", "nbLots", 1);

    @ParameterizedTest(name = "« {0} » → {1}")
    @CsvSource(delimiter = '|', value = {
            "garantieSoumission = OUI                          | true",
            "garantieSoumission = NON                          | false",
            "garantieSoumission != NON                         | true",
            "garantieSoumission = oui                          | true",
            "provenance = IMPORTEES et typePrix = UNITAIRES    | true",
            "provenance = NATIONAL ou typePrix = UNITAIRES     | true",
            "provenance = NATIONAL et typePrix = UNITAIRES     | false",
            "provenance = NATIONAL OU typePrix = FORFAIT       | false",
            "avance = OUI                                      | false",
            "avance != OUI                                     | true",
            "nbLots = 1                                        | true",
            "nbLots != 1                                       | false",
            "n'importe quoi                                    | false",
            "garantieSoumission = OUI et                       | false",
            "= OUI                                             | false"
    })
    void cas(String condition, boolean attendu) {
        assertThat(ConditionCadrage.vraie(condition, CADRAGE)).isEqualTo(attendu);
    }

    @Test
    @DisplayName("nulle ou vide → vrai ; cadrage nul → clé absente")
    void videEtNul() {
        assertThat(ConditionCadrage.vraie(null, CADRAGE)).isTrue();
        assertThat(ConditionCadrage.vraie("   ", CADRAGE)).isTrue();
        assertThat(ConditionCadrage.vraie("avance = OUI", null)).isFalse();
        assertThat(ConditionCadrage.vraie("avance != OUI", null)).isTrue();
    }

    @Test
    @DisplayName("lisible : ce que l'écriture du référentiel accepte")
    void lisible() {
        assertThat(ConditionCadrage.lisible(null)).isTrue();
        assertThat(ConditionCadrage.lisible("garantieSoumission = OUI et avance != NON ou alloti=OUI")).isTrue();
        assertThat(ConditionCadrage.lisible("garantieSoumission OUI")).isFalse();
        assertThat(ConditionCadrage.lisible("garantieSoumission = O UI")).isFalse();
        assertThat(ConditionCadrage.lisible("garantieSoumission = OUI et")).isFalse();
        assertThat(ConditionCadrage.lisible("provenance = étrangère")).isFalse();
    }
}
