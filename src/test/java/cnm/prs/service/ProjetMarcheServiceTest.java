package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ⚠️ 2026-10-10 (demande front « projet-de-marche ») — l'objet du seul lot (art. 28 : l'énumération des autres lots est retirée) et la base
 * légale selon le mode de passation (art. 60 : les articles de la loi en vertu desquels le marché est passé).
 */
class ProjetMarcheServiceTest {

    private static final String PROCEDURE_40 = "Travaux d'aménagement et d'entretien des voiries au sein des campus universitaires dans les "
            + "chefs-lieux de province répartis en cinq (05) lots : Lot n°01 : campus universitaire d'Antsiranana - Lot n°02 : campus "
            + "universitaire de Mahajanga - Lot n°03 : campus universitaire de Fianarantsoa - Lot n°04 : campus universitaire de Toamasina - "
            + "Lot n°05 : campus universitaire de Toliara";

    @Test
    @DisplayName("L'objet du marché : celui de l'appel d'offres sans l'énumération des lots, puis le seul lot du marché")
    void objetDuLot() {
        assertThat(ProjetMarcheService.objetDuMarche(PROCEDURE_40, "Lot n°01 : campus universitaire d'Antsiranana"))
                .isEqualTo("Travaux d'aménagement et d'entretien des voiries au sein des campus universitaires dans les chefs-lieux de province"
                        + " — Lot n°01 : campus universitaire d'Antsiranana");
        assertThat(ProjetMarcheService.objetDuMarche("Fournitures de bureau en deux lots", "Lot n°02"))
                .isEqualTo("Fournitures de bureau — Lot n°02");
        assertThat(ProjetMarcheService.objetDuMarche("Fournitures de bureau (lot 1 : papier ; lot 2 : encre)", "Lot n°01 : papier"))
                .isEqualTo("Fournitures de bureau — Lot n°01 : papier");
        // Sans allotissement : l'objet tel quel ; sans objet : à compléter.
        assertThat(ProjetMarcheService.objetDuMarche("Acquisition de véhicules", null)).isEqualTo("Acquisition de véhicules");
        assertThat(ProjetMarcheService.objetDuMarche(null, null)).isEqualTo("……");
    }

    @Test
    @DisplayName("La base légale : l'article du mode de passation et l'article 60 ; mode inconnu : à compléter ; international")
    void baseLegale() {
        assertThat(ProjetMarcheService.baseLegale("Appel d'offres ouvert", false)).contains("appel d'offres ouvert", "articles 35 et 60");
        assertThat(ProjetMarcheService.baseLegale("Appel d'offres ouvert avec pré-qualification", false)).contains("articles 36 et 60");
        assertThat(ProjetMarcheService.baseLegale("Appel d'offres en deux étapes", false)).contains("articles 37 et 60");
        assertThat(ProjetMarcheService.baseLegale("Appel d'offres restreint", false)).contains("articles 38 et 60");
        assertThat(ProjetMarcheService.baseLegale("Marché de gré à gré", false)).contains("articles 39 et 60");
        assertThat(ProjetMarcheService.baseLegale("Consultation de prix", false)).contains("articles 41 et 60");
        assertThat(ProjetMarcheService.baseLegale("Appel d'offres ouvert", true)).contains("prestations intellectuelles", "articles 42 et 60");
        assertThat(ProjetMarcheService.baseLegale("Appel d'offres ouvert international", false)).contains("ouvert international", "articles 35 et 60");
        assertThat(ProjetMarcheService.baseLegale(null, false)).contains("articles …… et 60");
        assertThat(ProjetMarcheService.international("Appel d'offres restreint international")).isTrue();
        assertThat(ProjetMarcheService.international("Appel d'offres ouvert")).isFalse();
    }
}
