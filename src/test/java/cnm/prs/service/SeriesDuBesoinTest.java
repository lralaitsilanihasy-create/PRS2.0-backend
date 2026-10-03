package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.dto.FicheMarcheDto;

/**
 * ⚠️ V59 (demande front du 2026-10-02, DQE des travaux, §B1.5) — le jeton {@code {{BESOIN.series}}} : une ligne par série
 * du DQE, dans l'ordre de première apparition, l'intitulé porté par un seul article valant pour toute la série ; une
 * ligne allotie au même DQE dans chaque lot donne une seule liste, sinon une liste par lot ; un document établi par lot lit
 * celle de son lot ; sans DQE, des pointillés.
 */
class SeriesDuBesoinTest {

    private static BesoinFiche.Article article(Integer lot, String numero, String serie, String libelle) {
        return new BesoinFiche.Article(lot, 1, "Article " + numero, "m³", null, null, BigDecimal.ONE, List.of(), numero, serie,
                libelle, null, false, null);
    }

    private static String rendre(Map<String, String> jetons, Integer lot) {
        FicheMarcheDto f = new FicheMarcheDto();
        f.setIdDetail(1);
        f.setVersion(1);
        f.setTypeMarche("QUANTITE_FIXE");
        f.setCategorie("TRAVAUX");
        f.setCadrage(new LinkedHashMap<>());
        f.setValeurs(new HashMap<>());
        f.setValeursPpm(new HashMap<>());
        return FormulairesCandidat.rendreModele("CCAP", lot, f, Map.of(),
                FichierCommande.lireModele("PARA\tDécoupage :\nPARA\t{{BESOIN.series}}\n"), null, jetons).texte();
    }

    @Test
    @DisplayName("Ligne non allotie (MTP) : « 000 — Installation : ……… % », une ligne par série, l'intitulé recopié")
    void nonAllotie() {
        Map<String, String> m = FormulairesCandidat.seriesDuBesoin(List.of(article(null, "001", "000", "Installation"),
                article(null, "529", "500", "Ouvrages"), article(null, "530", "500", null), article(null, "601", "600", null),
                article(null, "602", "600", "Chaussées")), 0);
        assertThat(m).containsOnlyKeys("BESOIN.series");
        assertThat(m.get("BESOIN.series")).isEqualTo("000 — Installation : ……… %\n500 — Ouvrages : ……… %\n600 — Chaussées : ……… %");
        assertThat(rendre(m, null)).contains("500 — Ouvrages : ……… %");
    }

    @Test
    @DisplayName("Ligne allotie au même DQE (MEN) : une seule liste ; DQE différents : une liste par lot ; document par lot : "
            + "celle de son lot")
    void allotie() {
        Map<String, String> men = FormulairesCandidat.seriesDuBesoin(List.of(article(1, "0.1", "0", "Installation"),
                article(1, "1.1", "1", "Terrassement"), article(2, "0.1", "0", "Installation"), article(2, "1.1", "1", "Terrassement")), 2);
        assertThat(men.get("BESOIN.series")).isEqualTo("0 — Installation : ……… %\n1 — Terrassement : ……… %");

        Map<String, String> differents = FormulairesCandidat.seriesDuBesoin(List.of(article(1, "0.1", "0", "Installation"),
                article(2, "0.1", "0", "Installation"), article(2, "5.1", "5", "Menuiserie")), 2);
        assertThat(differents.get("BESOIN.series")).isEqualTo(
                "Lot 1 :\n0 — Installation : ……… %\nLot 2 :\n0 — Installation : ……… %\n5 — Menuiserie : ……… %");
        assertThat(rendre(differents, 2)).contains("5 — Menuiserie").doesNotContain("Lot 1 :");
    }

    @Test
    @DisplayName("Sans DQE (ou un besoin de fournitures, sans série) : rien, le jeton s'imprime en pointillés")
    void sansDqe() {
        assertThat(FormulairesCandidat.seriesDuBesoin(List.of(), 0)).isEmpty();
        assertThat(FormulairesCandidat.seriesDuBesoin(List.of(new BesoinFiche.Article(null, 1, "Ordinateur", "U", null, null,
                BigDecimal.ONE, List.of())), 0)).isEmpty();
        assertThat(rendre(Map.of(), null)).contains(FormulairesCandidat.POINTILLES).doesNotContain("{{");
    }
}
