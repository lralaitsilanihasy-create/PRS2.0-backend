package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ⚠️ 2026-10-02 (recette du DAO du MEN, §B4) — le nettoyage des caractères de remplacement « ¿ » (U+00BF) à l'import du
 * plan : seules les règles sans ambiguïté s'appliquent, le reste est signalé (ENCODAGE_SUSPECT) et jamais deviné. Pur.
 */
class NettoyageEncodagePpmTest {

    @Test
    @DisplayName("Élision d'un mot d'une lettre ou de « qu » devant une lettre ; ligature œ ; « jusqu¿à » ; un « ¿ » ambigu "
            + "(entre deux espaces, en milieu de mot) reste, pour être signalé")
    void nettoyage() {
        assertThat(SaisiePpmImportService.nettoyerEncodage("Travaux d¿aménagement et d¿entretien des voiries"))
                .isEqualTo("Travaux d'aménagement et d'entretien des voiries");
        assertThat(SaisiePpmImportService.nettoyerEncodage("reliant le croisement Mazava Huile à l¿Université"))
                .isEqualTo("reliant le croisement Mazava Huile à l'Université");
        assertThat(SaisiePpmImportService.nettoyerEncodage("Organisation d¿évènement ; qu¿une ; L¿Etat"))
                .isEqualTo("Organisation d'évènement ; qu'une ; L'Etat");
        // La ligature passe avant l'élision : « c¿ur » est « cœur », pas « c'ur ».
        assertThat(SaisiePpmImportService.nettoyerEncodage("le c¿ur de ville")).isEqualTo("le cœur de ville");
        assertThat(SaisiePpmImportService.nettoyerEncodage("main d¿¿uvre, jusqu¿à la réception"))
                .isEqualTo("main d'œuvre, jusqu'à la réception");
        assertThat(SaisiePpmImportService.nettoyerEncodage("campus d'Antsiranana ¿ Lot n°02 ; fa¿ade"))
                .as("ambigus : signalés, jamais devinés").isEqualTo("campus d'Antsiranana ¿ Lot n°02 ; fa¿ade");
        assertThat(SaisiePpmImportService.nettoyerEncodage(null)).isNull();
    }
}
