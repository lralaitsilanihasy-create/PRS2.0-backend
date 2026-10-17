package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ⚠️ 2026-10-17 (demande front « numeros-a-deux-niveaux ») — un détail quantitatif à chapitres romains et à numéros
 * « chapitre.article », comme celui des bâtiments scolaires du MEN : B1 (numéro à deux niveaux, rang après le chapitre), B2
 * (chapitre romain = ligne de section, jamais un article). Tableaux construits à la main : la recette sur le DAO réel est dans
 * {@link LectureTableauxRecetteTest}.
 */
class LectureTableauxNumerosTest {

    private static LectureTableaux.Ligne ligne(String... textes) {
        List<LectureTableaux.Cellule> cellules = new ArrayList<>();
        for (int j = 0; j < textes.length; j++) {
            cellules.add(new LectureTableaux.Cellule(j, j + 1, textes[j].isEmpty() ? List.of() : List.of(textes[j]), false, textes[j]));
        }
        return new LectureTableaux.Ligne(cellules);
    }

    private static LectureTableaux.Tableau dqe(LectureTableaux.Ligne... lignes) {
        List<LectureTableaux.Ligne> toutes = new ArrayList<>();
        toutes.add(ligne("N°", "DESIGNATION", "UNITE", "QUANTITE"));
        toutes.addAll(List.of(lignes));
        return new LectureTableaux.Tableau(44, toutes, "LOT 1 :", new double[0], 4, false);
    }

    @Test
    @DisplayName("B1-B2 : chapitres romains en séries, articles « 1.1 » et « 2 .2 » rangés dans l'ordre du DQE")
    void chapitresRomainsEtNumerosADeuxNiveaux() {
        List<LectureTableaux.Article> lus = LectureTableaux.articles(List.of(dqe(
                ligne("0", "Travaux préparatoires", "", ""),
                ligne("0.1", "Installation de chantier", "fft", "1"),
                ligne("I", "Terrassement", "", ""),
                ligne("1.1", "Débroussaillage et décapage", "m2", "332,18"),
                ligne("", "Total terrassement", "", ""),
                ligne("II", "Béton", "", ""),
                ligne("2 .2", "Béton armé dosé à 350 kg/m3", "m3", "12"),
                ligne("10.1", "Peinture", "m2", "40"),
                ligne("III", "Chapitre avec unité (ignoré)", "m2", "3"))));

        assertThat(lus).extracting(a -> a.code).containsExactly("0.1", "1.1", "2.2", "10.1");
        assertThat(lus).extracting(a -> a.n).containsExactly(1001, 2001, 3002, 11001);
        assertThat(lus).extracting(a -> a.serie).containsExactly("0", "I", "II", "II");
        assertThat(lus.get(1).serieLibelle).isEqualTo("Terrassement");
        assertThat(lus.get(1).quantite).isEqualByComparingTo("332.18");
        assertThat(lus.get(2).unite).isEqualTo("m3");
    }

    @Test
    @DisplayName("Un numéro simple se lit comme avant, astérisque compris")
    void numeroSimpleInchange() {
        List<LectureTableaux.Article> lus = LectureTableaux.articles(List.of(dqe(
                ligne("000", "Installation", "", ""),
                ligne("001", "Amenée du matériel", "fft", "1"),
                ligne("540*", "Béton de propreté", "m3", "5"))));

        assertThat(lus).extracting(a -> a.code).containsExactly("001", "540");
        assertThat(lus).extracting(a -> a.n).containsExactly(1, 540);
        assertThat(LectureTableaux.numeroDe("1000")).isNull();
        assertThat(LectureTableaux.numeroDe("2.2.1")).isNull();
    }
}
