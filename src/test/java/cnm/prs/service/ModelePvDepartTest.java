package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

/**
 * ⚠️ 2026-10-21 (« modele-pv-depart », puis « modele-pv-page » §B5) — le modèle de départ est une page reconstruite : valide pour
 * toutes les familles et portées, sans ancien chevron, avec ses blocs d'avis, son annexe (saut de page compris) et, pour le dossier de
 * marché, ses titulaires ; rendu en une page sans réserves, deux avec l'annexe.
 */
class ModelePvDepartTest {

    private static String textes(JsonNode n) {
        StringBuilder s = new StringBuilder();
        if (n.path("text").isString()) {
            s.append(n.path("text").asString());
        }
        for (JsonNode e : n.isObject() ? n.values() : n) {
            if (e.isObject() || e.isArray()) {
                s.append(textes(e));
            }
        }
        return s.toString();
    }

    @Test
    void valideSansChevronPourChaqueFamilleEtPortee() {
        for (ModelePvDepart.Famille f : ModelePvDepart.FAMILLES.values()) {
            for (String portee : ModelePvDepart.PORTEES) {
                ModelePvDepart.Depart d = ModelePvDepart.assembler(f, portee, 3);
                ModelePvPage.valider(d.document(), id -> id == 3);
                assertThat(textes(d.document())).doesNotContain("<", "{{").contains("PROCÈS-VERBAL D’EXAMEN " + f.titre());
                List<String> codes = ModelePvPage.relever(d.document()).marqueurs().stream().map(m -> m.type() + ":" + m.code()).toList();
                assertThat(codes).contains("VARIABLE:PV.REFERENCE", "VARIABLE:PV.MEMBRE", "SI:FAVORABLE", "SI:AVEC_RESERVES",
                        "SI:DEFAVORABLE", "SAISI:motif_defavorable", "SAISI:observation_generale", "POUR:OBSERVATIONS");
                assertThat(codes.contains("POUR:TITULAIRES")).isEqualTo(f.titulaires());
                assertThat(codes.contains("SI:CENTRALE")).isEqualTo(ModelePvDepart.LES_DEUX.equals(portee));
                assertThat(d.conditions()).hasSize(ModelePvDepart.LES_DEUX.equals(portee) ? 6 : 4);
            }
        }
    }

    @Test
    void uneOuDeuxPagesSelonLAvis() throws Exception {
        ModelePvDepart.Depart d = ModelePvDepart.assembler(ModelePvDepart.FAMILLES.get("DDM"), ModelePvDepart.LES_DEUX, 3);
        Map<String, String> cond = new LinkedHashMap<>();
        d.conditions().forEach(c -> cond.put(c.nom(), c.expression()));
        ModelePvWord.Contexte ex = CatalogueModelesPv.exemple();
        for (String avis : List.of("FAV", "FAVR", "DEF")) {
            Map<String, String> cles = new java.util.HashMap<>(ex.cles());
            cles.put("avis", avis);
            cles.put("portee", "regionale");
            ModelePvWord.Contexte ctx = new ModelePvWord.Contexte(ex.variables(), ex.listes(), cles, Map.of("motif_defavorable", "pièces manquantes"));
            byte[] pdf = ModelePvPage.pdf(ModelePvPage.xhtml(d.document(), ctx, cond, id -> null));
            try (PDDocument doc = Loader.loadPDF(pdf)) {
                String t = trie().getText(doc).replaceAll("\\s+", " ");
                assertThat(doc.getNumberOfPages()).as(avis).isEqualTo("FAVR".equals(avis) ? 2 : 1);
                assertThat(t).contains("Commission Régionale des Marchés", ex.variables().get("COMMISSION.LOCALITE").texte(),
                        "Titulaire(s) proposé(s)", "Hygiène & Services Madagascar SARL").doesNotContain("Centrale", "{{");
                assertThat(t.contains("ANNEXE")).isEqualTo("FAVR".equals(avis));
                if ("DEF".equals(avis)) {
                    assertThat(t).contains("AVIS NON FAVORABLE à l’attribution du marché, pour le motif suivant : pièces manquantes.");
                }
            }
        }
    }

    /** Le texte dans l’ordre de la page (un texte justifié ou un compteur sinon sortent dans le désordre). */
    static PDFTextStripper trie() throws java.io.IOException {
        PDFTextStripper s = new PDFTextStripper();
        s.setSortByPosition(true);
        return s;
    }
}
