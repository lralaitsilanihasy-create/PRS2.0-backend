package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * ⚠️ 2026-10-22 (demande front « modeles-documents-marche », W1) — le Word d'une page, écrit depuis le document résolu : réglages de
 * page, en-tête et pied (champs de page), titres, marques, retours à la ligne, listes, tableaux (fusions, fonds, bordures), sauts de
 * page, passages et variables résolus, mention d'exemple. Le rendu a été comparé en image au PDF (Word → PDF) : mêmes pages.
 */
class ModelePvDocxTest {

    private static final String DOCUMENT = """
            {"page":{"police":"Arial","taille":11,"orientation":"paysage","marges":{"haut":20,"bas":20,"gauche":20,"droite":20}},
             "entete":{"type":"doc","content":[{"type":"paragraph","attrs":{"textAlign":"right"},"content":[{"type":"text","text":"En-tête"}]}]},
             "piedDePage":{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"Page "},{"type":"numeroPage"},
               {"type":"text","text":" sur "},{"type":"nombrePages"}]}]},
             "corps":{"type":"doc","content":[
              {"type":"heading","attrs":{"level":1,"textAlign":"center"},"content":[{"type":"text","text":"Titre"}]},
              {"type":"paragraph","attrs":{"textAlign":"justify","retraitPremiereLigne":10},"content":[{"type":"text","text":"Avis "},
               {"type":"variable","attrs":{"code":"PV.AVIS"},"marks":[{"type":"bold"}]},{"type":"bloc","attrs":{"condition":"NON"},
               "content":[{"type":"text","text":" jamais"}]},{"type":"hardBreak"},{"type":"champ","attrs":{"code":"obs"}}]},
              {"type":"paragraph","content":[{"type":"bloc","attrs":{"condition":"NON"},"content":[{"type":"text","text":"retiré"}]}]},
              {"type":"bulletList","content":[{"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"point"}]}]}]},
              {"type":"orderedList","attrs":{"start":3},"content":[{"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"trois"}]}]}]},
              {"type":"table","attrs":{"bordures":true},"content":[
               {"type":"tableRow","content":[{"type":"tableHeader","attrs":{"colwidth":[100]},"content":[{"type":"paragraph","content":[{"type":"text","text":"A"}]}]},
                {"type":"tableHeader","attrs":{"colspan":2,"colwidth":[150,150],"fond":"#D9D9D9"},"content":[{"type":"paragraph","content":[{"type":"text","text":"B"}]}]}]},
               {"type":"tableRow","content":[{"type":"tableCell","attrs":{"rowspan":2},"content":[{"type":"paragraph","content":[{"type":"text","text":"fusion"}]}]},
                {"type":"tableCell","content":[{"type":"paragraph","content":[{"type":"text","text":"b1"}]}]},
                {"type":"tableCell","content":[{"type":"paragraph","content":[{"type":"text","text":"c1"}]}]}]},
               {"type":"tableRow","content":[{"type":"tableCell","content":[{"type":"paragraph","content":[{"type":"text","text":"b2"}]}]},
                {"type":"tableCell","content":[{"type":"paragraph","content":[{"type":"text","text":"c2"}]}]}]}]},
              {"type":"table","content":[{"type":"tableRow","attrs":{"liste":"OBSERVATIONS"},"content":[{"type":"tableCell","content":[
                {"type":"paragraph","content":[{"type":"variable","attrs":{"code":"OBS.POINT"}}]}]}]}]},
              {"type":"pageBreak"},
              {"type":"paragraph","content":[{"type":"text","text":"Fin"}]}]}}""";

    @Test
    void wordDuDocumentResolu() throws Exception {
        JsonNode doc = new ObjectMapper().readTree(DOCUMENT);
        ModelePvWord.Contexte ex = CatalogueModelesPv.exemple();
        ModelePvWord.Contexte ctx = new ModelePvWord.Contexte(ex.variables(), ex.listes(), ex.cles(), Map.of("obs", "ligne 1\nligne 2"));
        JsonNode resolu = ModelePvPage.resoudre(doc, ctx, Map.of("NON", "avis = DEF"));
        assertThat(resolu.toString()).doesNotContain("\"bloc\"", "\"variable\"", "\"champ\"", "\"liste\"", "retiré", "jamais");

        byte[] docx = ModelePvDocx.docx(resolu, id -> null, "EXEMPLE — valeurs fictives");
        try (XWPFDocument d = new XWPFDocument(new ByteArrayInputStream(docx))) {
            var sect = d.getDocument().getBody().getSectPr();
            assertThat(sect.xmlText()).contains("w:w=\"16838\"", "w:orient=\"landscape\"", "w:left=\"1134\"");
            assertThat(d.getHeaderList()).hasSize(1);
            assertThat(d.getHeaderList().get(0).getText()).contains("En-tête");
            String pied = d.getFooterList().get(0).getText();
            assertThat(pied).contains("Page ", " sur ", "EXEMPLE — valeurs fictives");
            assertThat(d.getFooterList().get(0).getParagraphs().get(0).getCTP().xmlText()).contains("PAGE", "NUMPAGES");

            List<XWPFParagraph> ps = d.getParagraphs();
            assertThat(ps.get(0).getText()).isEqualTo("Titre");
            assertThat(ps.get(0).getAlignment()).isEqualTo(ParagraphAlignment.CENTER);
            assertThat(ps.get(0).getRuns().get(0).isBold()).isTrue();
            assertThat(ps.get(0).getRuns().get(0).getFontSizeAsDouble()).isEqualTo(16.5);
            assertThat(ps.get(1).getText()).isEqualTo("Avis favorable\nligne 1\nligne 2");
            assertThat(ps.get(1).getAlignment()).isEqualTo(ParagraphAlignment.BOTH);
            assertThat(ps.get(1).getRuns().get(1).isBold()).isTrue();
            assertThat(ps.get(2).getText()).isEqualTo("•\tpoint");
            assertThat(ps.get(3).getText()).isEqualTo("3.\ttrois");
            assertThat(d.getParagraphs().stream().map(XWPFParagraph::getText)).contains("Fin").doesNotContain("retiré");
            assertThat(d.getDocument().xmlText()).contains("w:br w:type=\"page\"");

            // Le tableau : la ligne répétée dépliée (deux observations), la fusion verticale et horizontale, le fond.
            XWPFTable t = d.getTables().get(0);
            assertThat(t.getRows()).hasSize(3);
            assertThat(t.getRow(0).getCell(1).getCTTc().getTcPr().getGridSpan().getVal().intValue()).isEqualTo(2);
            assertThat(t.getRow(0).getCell(1).getCTTc().getTcPr().xmlText()).contains("w:fill=\"D9D9D9\"");
            assertThat(t.getRow(0).getCell(0).getParagraphs().get(0).getRuns().get(0).isBold()).isTrue();
            assertThat(t.getRow(1).getCell(0).getText()).isEqualTo("fusion");
            assertThat(t.getRow(1).getCell(0).getCTTc().getTcPr().getVMerge().getVal().toString()).isEqualTo("restart");
            assertThat(t.getRow(2).getCell(0).getCTTc().getTcPr().getVMerge().getVal().toString()).isEqualTo("continue");
            assertThat(t.getRow(2).getCell(1).getText()).isEqualTo("b2");
            assertThat(t.getRow(2).getCell(2).getText()).isEqualTo("c2");
            XWPFTable obs = d.getTables().get(1);
            assertThat(obs.getRows()).hasSize(2);
            assertThat(obs.getRow(0).getCell(0).getText()).isEqualTo("[Dossier] Pièces administratives");
            assertThat(d.getSettings().getCTSettings().xmlText()).contains("doNotExpandShiftReturn");
        }
    }

    private static final String ARTICLES = """
            {"corps":{"type":"doc","content":[
              {"type":"heading","attrs":{"level":1,"numerotation":"ARTICLE {n} : ","id":"objet"},"content":[{"type":"text","text":"Objet","marks":[{"type":"bold"}]}]},
              {"type":"bloc","attrs":{"condition":"NON"},"content":[
                {"type":"heading","attrs":{"level":1,"numerotation":"ARTICLE {n} : ","id":"retire"},"content":[{"type":"text","text":"Retiré"}]}]},
              {"type":"heading","attrs":{"level":1,"numerotation":"ARTICLE {n} : ","id":"prix"},"content":[{"type":"text","text":"Prix"}]},
              {"type":"heading","attrs":{"level":2,"numerotation":"{n}. "},"content":[{"type":"text","text":"Révision"}]},
              {"type":"heading","attrs":{"level":2,"numerotation":"{n}. "},"content":[{"type":"text","text":"Avance"}]},
              {"type":"heading","attrs":{"level":1},"content":[{"type":"text","text":"Annexe sans numéro"}]},
              {"type":"heading","attrs":{"level":1,"numerotation":"Article {n} — ","id":"fin"},"content":[{"type":"text","text":"Fin"}]},
              {"type":"heading","attrs":{"level":2,"numerotation":"{n}. "},"content":[{"type":"text","text":"Premier sous-titre après"}]},
              {"type":"paragraph","content":[{"type":"text","text":"Voir l'article "},{"type":"renvoi","attrs":{"cible":"prix"}},
                {"type":"text","text":" et l'article "},{"type":"renvoi","attrs":{"cible":"retire"},"marks":[{"type":"bold"}]},{"type":"text","text":"."}]}]}}""";

    /**
     * ⚠️ 2026-10-22 (« modeles-documents-marche », W2) — les titres numérotés : comptés après les règles (sans trou), un compteur par
     * niveau remis à zéro sous un titre supérieur, les titres sans gabarit ne comptent pas ; un renvoi prend le numéro de sa cible,
     * « …… » quand elle est retirée ; le contrôle signale une cible inconnue, la validation un id en double ou un gabarit sans {n}.
     */
    @Test
    void titresNumerotesEtRenvois() throws Exception {
        JsonNode doc = new ObjectMapper().readTree(ARTICLES);
        ModelePvPage.valider(doc, id -> false);
        assertThat(ModelePvPage.renvoisSansCible(doc)).isEmpty();
        ModelePvWord.Contexte ex = CatalogueModelesPv.exemple();
        JsonNode resolu = ModelePvPage.resoudre(doc, ex, Map.of("NON", "avis = DEF"));

        byte[] docx = ModelePvDocx.docx(resolu, id -> null, null);
        try (XWPFDocument d = new XWPFDocument(new ByteArrayInputStream(docx))) {
            assertThat(d.getParagraphs().stream().map(XWPFParagraph::getText).toList()).containsExactly("ARTICLE 1 : Objet",
                    "ARTICLE 2 : Prix", "1. Révision", "2. Avance", "Annexe sans numéro", "Article 3 — Fin", "1. Premier sous-titre après",
                    "Voir l'article 2 et l'article …….");
            assertThat(d.getParagraphs().get(0).getRuns().get(0).isBold()).isTrue();
        }
        String xhtml = ModelePvPage.xhtmlResolu(resolu, id -> null, null);
        assertThat(xhtml).contains("ARTICLE 2 : Prix", "Article 3 — Fin", "Voir l'article 2 et l'article ").doesNotContain("Retiré");

        ObjectNode faux = (ObjectNode) new ObjectMapper().readTree(ARTICLES);
        ObjectNode h = (ObjectNode) faux.path("corps").path("content").path(2).path("attrs");
        ((ObjectNode) faux.path("corps").path("content").path(3).path("attrs")).put("id", "objet");
        h.put("numerotation", "ARTICLE : ");
        ((ObjectNode) faux.path("corps").path("content").path(8).path("content").path(1).path("attrs")).put("cible", "inconnu");
        assertThatThrownBy(() -> ModelePvPage.valider(faux, id -> false)).isInstanceOf(ModelePvPage.Invalide.class)
                .satisfies(e -> assertThat(((ModelePvPage.Invalide) e).details().values().toString())
                        .contains("déjà employé", "contient {n}"));
        assertThat(ModelePvPage.renvoisSansCible(faux)).hasSize(1).containsValue("corps › paragraphe 1 › renvoi 1 : « inconnu »");
    }
}
