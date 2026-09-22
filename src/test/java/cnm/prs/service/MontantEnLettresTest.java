package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * ⚠️ Fiche marché DAO (demande front du 2026-09-22, §B3, test 12) — {@link NombreEnLettres#cardinal(long)} étendu aux
 * millions et milliards : un cas par palier demandé, plus les invariants déjà couverts (« et un », « quatre-vingts »,
 * « deux cents »).
 */
class MontantEnLettresTest {

    @ParameterizedTest(name = "{0} → {1}")
    @DisplayName("Paliers : 999 999 · 1 000 000 · 1 000 001 · 999 999 999 · 1 000 000 000 · 1 200 000 000")
    @CsvSource(delimiter = '|', value = {
            "999999        | neuf cent quatre-vingt-dix-neuf mille neuf cent quatre-vingt-dix-neuf ariary",
            "1000000       | un million ariary",
            "1000001       | un million un ariary",
            "8400000       | huit millions quatre cent mille ariary",
            "999999999     | neuf cent quatre-vingt-dix-neuf millions neuf cent quatre-vingt-dix-neuf mille neuf cent quatre-vingt-dix-neuf ariary",
            "1000000000    | un milliard ariary",
            "1200000000    | un milliard deux cents millions ariary",
            "2000000000    | deux milliards ariary",
            "2000000021    | deux milliards vingt et un ariary",
            "0             | zéro ariary",
            "80            | quatre-vingts ariary",
            "200           | deux cents ariary",
            "1200.99       | mille deux cents ariary"
    })
    void paliers(String montant, String attendu) {
        assertThat(MontantEnLettres.ariary(new BigDecimal(montant))).isEqualTo(attendu);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @DisplayName("cardinal : l'existant sous le million ne bouge pas")
    @CsvSource(delimiter = '|', value = {
            "21     | vingt et un",
            "71     | soixante et onze",
            "81     | quatre-vingt-un",
            "100    | cent",
            "1000   | mille",
            "2026   | deux mille vingt-six",
            "-5     | moins cinq"
    })
    void cardinalInchange(long n, String attendu) {
        assertThat(NombreEnLettres.cardinal(n)).isEqualTo(attendu);
    }

    @DisplayName("null → null")
    @org.junit.jupiter.api.Test
    void nul() {
        assertThat(MontantEnLettres.ariary(null)).isNull();
    }
}
