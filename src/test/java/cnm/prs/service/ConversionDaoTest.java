package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * ⚠️ 2026-10-22 (demande front « modeles-dao », D1-D3) — la conversion d'un fichier de commande du DAO en page, puis de la page en
 * fichier, est <strong>sans perte</strong> pour les 15 fichiers intégrés : mêmes éléments (styles, textes, tableaux, cellules), mêmes
 * règles déclarées, après un passage par le JSON enregistré ; la page est conforme au schéma et rien n'y est « non rendu ».
 */
class ConversionDaoTest {

    static List<String> sigles() {
        return List.copyOf(new ModelesDao().modeles().keySet());
    }

    @ParameterizedTest
    @MethodSource("sigles")
    void allerRetourSansPerte(String sigle) throws IOException {
        String contenu;
        try (InputStream in = ConversionDaoTest.class.getResourceAsStream("/modeles/dao/" + sigle + ".txt")) {
            contenu = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        FichierCommande.Modele m = FichierCommande.lireModele(contenu);
        ConversionDao.Page p = ConversionDao.enPage(m);
        ObjectMapper json = new ObjectMapper();
        JsonNode stocke = json.readTree(json.writeValueAsString(p.document()));
        ModelePvPage.valider(stocke, id -> false);
        assertThat(ConversionDao.nonRendus(stocke)).isEmpty();
        assertThat(ConversionDao.passagesEnLigne(stocke)).isEmpty();
        Map<String, String> conditions = new LinkedHashMap<>();
        p.conditions().forEach(c -> conditions.put(c.nom(), c.expression()));
        FichierCommande.Modele retour = ConversionDao.enModele(stocke, conditions);
        assertThat(retour.conditions()).isEqualTo(m.conditions());
        assertThat(retour.elements()).hasSameSizeAs(m.elements());
        for (int i = 0; i < m.elements().size(); i++) {
            assertThat(texte(retour.elements().get(i))).as(sigle + " élément " + i).isEqualTo(texte(m.elements().get(i)));
        }
        assertThat(retour.elements()).isEqualTo(m.elements());
    }

    private static String texte(DocumentLibre.Element e) {
        if (e instanceof DocumentLibre.Paragraphe p) {
            return p.style() + " " + p.texte();
        }
        if (e instanceof DocumentLibre.Tableau t) {
            return "TABLE " + t.colonnes() + " " + t.lignes();
        }
        return e.toString();
    }
}
