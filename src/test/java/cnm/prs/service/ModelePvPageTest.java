package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * ⚠️ 2026-10-21 (demande front « modele-pv-page », lots P1-P2) — le document de la page : validation sur les nœuds fermés (chemin et
 * lieu du défaut), relevé pour le contrôle, rendu XHTML (blocs, blocs en ligne, lignes répétées, échappement) et PDF (polices
 * Liberation embarquées, sauts de page). Les aides {@code document}, {@code paragraphe}, {@code bloc}, {@code tableau} servent aussi
 * aux tests d'intégration.
 */
public class ModelePvPageTest {

    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    // ───────────────────────────────────────────── aides ─────────────────────────────────────────────

    /** Un document : le corps donné, A4 portrait, un pied de page « Page n / N ». */
    public static ObjectNode document(JsonNode... corps) {
        ObjectNode doc = F.objectNode();
        doc.putObject("page").put("format", "A4").put("police", "Arial").put("taille", 10);
        ArrayNode c = doc.putObject("corps").put("type", "doc").putArray("content");
        for (JsonNode n : corps) {
            c.add(n);
        }
        ObjectNode pied = ModelePvPage.noeud("paragraph");
        pied.putArray("content").add(ModelePvPage.noeud("text").put("text", "Page "))
                .add(ModelePvPage.noeud("numeroPage")).add(ModelePvPage.noeud("text").put("text", " / "))
                .add(ModelePvPage.noeud("nombrePages"));
        doc.putObject("piedDePage").put("type", "doc").putArray("content").add(pied);
        return doc;
    }

    /** Un paragraphe écrit avec les marqueurs du Word ({@code {{CODE}}}, {@code {{SAISI:x}}}, {@code {{SI:X}}…{{FINSI:X}}}). */
    public static ObjectNode paragraphe(String texte) {
        ObjectNode p = ModelePvPage.noeud("paragraph");
        p.set("content", ModelePvPage.enLigne(texte, null));
        return p;
    }

    public static ObjectNode bloc(String condition, JsonNode... contenu) {
        ObjectNode b = ModelePvPage.noeud("bloc");
        b.putObject("attrs").put("condition", condition);
        ArrayNode a = b.putArray("content");
        for (JsonNode n : contenu) {
            a.add(n);
        }
        return b;
    }

    /** Un tableau : une ligne d'en-tête, puis une ligne répétée par {@code liste} (nulle : une ligne simple). */
    public static ObjectNode tableau(List<String> entete, String liste, List<String> ligne) {
        ObjectNode t = ModelePvPage.noeud("table");
        ArrayNode lignes = t.putArray("content");
        lignes.add(ligne(entete, null));
        lignes.add(ligne(ligne, liste));
        return t;
    }

    private static ObjectNode ligne(List<String> cellules, String liste) {
        ObjectNode l = ModelePvPage.noeud("tableRow");
        if (liste != null) {
            l.putObject("attrs").put("liste", liste);
        }
        ArrayNode a = l.putArray("content");
        for (String c : cellules) {
            ObjectNode cell = ModelePvPage.noeud("tableCell");
            cell.putArray("content").add(paragraphe(c));
            a.add(cell);
        }
        return l;
    }

    private static ModelePvWord.Contexte contexte(String avis, Map<String, String> saisis) {
        ModelePvWord.Contexte ex = CatalogueModelesPv.exemple();
        Map<String, String> cles = new java.util.HashMap<>(ex.cles());
        cles.put("avis", avis);
        return new ModelePvWord.Contexte(ex.variables(), ex.listes(), cles, saisis);
    }

    private static Map<String, Object> details(JsonNode doc) {
        try {
            ModelePvPage.valider(doc, id -> id == 7);
        } catch (ModelePvPage.Invalide e) {
            return e.details();
        }
        return Map.of();
    }

    // ───────────────────────────────────────────── tests ─────────────────────────────────────────────

    @Test
    void valideLesNoeudsFermes() {
        ObjectNode bon = document(paragraphe("PV {{PV.REFERENCE|majuscules}} — {{SI:A}}oui{{FINSI:A}} {{SAISI:obs}}"),
                tableau(List.of("Point"), "OBSERVATIONS", List.of("{{OBS.POINT}}")));
        assertThat(details(bon)).isEmpty();

        // Un nœud inconnu, une couleur hors liste, une image inconnue, une ligne hors d'un tableau : refusés, chacun à son chemin.
        ObjectNode script = F.objectNode().put("type", "script");
        ObjectNode rouge = paragraphe("x");
        ((ObjectNode) rouge.path("content").path(0)).putArray("marks").addObject().put("type", "textStyle").putObject("attrs")
                .put("color", "#FF00FF");
        ObjectNode image = ModelePvPage.noeud("image");
        image.putObject("attrs").put("idImage", 8);
        ObjectNode ligneSeule = (ObjectNode) tableau(List.of("a"), null, List.of("b")).path("content").path(0);
        Map<String, Object> d = details(document(script, rouge, image, ligneSeule));
        assertThat(d).containsOnlyKeys("corps/0", "corps/1/0", "corps/2", "corps/3");
        assertThat((String) d.get("corps/0")).startsWith("corps › nœud 1 : « script » n'est pas permis ici");
        assertThat((String) d.get("corps/1/0")).startsWith("corps › paragraphe 1 › texte 1 : color vaut");
        assertThat((String) d.get("corps/2")).contains("idImage ne désigne aucune image");
        assertThat((String) d.get("corps/3")).contains("« tableRow » n'est pas permis ici");

        // Une enveloppe sans corps, une clé inconnue, un attribut inconnu (nul toléré, valué refusé), une marge hors plage.
        ObjectNode sansCorps = F.objectNode().put("html", "<p>");
        assertThat(details(sansCorps)).containsKeys("document", "corps");
        ObjectNode p = paragraphe("x");
        p.putObject("attrs").putNull("dir").put("onclick", "alert(1)");
        ObjectNode marges = document(p);
        ((ObjectNode) marges.path("page")).putObject("marges").put("haut", 99);
        assertThat(details(marges)).containsOnlyKeys("page", "corps/0");
        assertThat((String) details(marges).get("corps/0")).contains("attribut inconnu « onclick »").doesNotContain("dir");
    }

    @Test
    void releveAvecLieuEtChemin() {
        ObjectNode imbriquee = tableau(List.of("x"), "OBSERVATIONS", List.of("{{OBS.POINT}}"));
        ObjectNode cellule = (ObjectNode) imbriquee.path("content").path(1).path("content").path(0);
        ((ArrayNode) cellule.path("content")).add(tableau(List.of("y"), "TITULAIRES", List.of("{{TIT.RANG}}")));
        ModelePvPage.Releve r = ModelePvPage.relever(document(paragraphe("Réf. {{PV.REFERENCE}}"),
                bloc("A", paragraphe("{{SAISI:obs}}")), imbriquee));
        List<String> vus = r.marqueurs().stream().map(m -> m.type() + " " + m.code() + " @" + m.ou() + " #" + r.chemins().get(m)).toList();
        assertThat(vus).containsExactly(
                "VARIABLE PV.REFERENCE @corps › paragraphe 1 › variable 1 #corps/0/1",
                "SI A @corps › bloc 1 #corps/1",
                "SAISI obs @corps › bloc 1 › paragraphe 1 › champ 1 #corps/1/0/0",
                "POUR OBSERVATIONS @corps › tableau 1 › ligne 2 #corps/2/1",
                "VARIABLE OBS.POINT @corps › tableau 1 › ligne 2 › cellule 1 › paragraphe 1 › variable 1 #corps/2/1/0/0/0",
                "POUR TITULAIRES @corps › tableau 1 › ligne 2 › cellule 1 › tableau 1 › ligne 2 #corps/2/1/0/1/1",
                "VARIABLE TIT.RANG @corps › tableau 1 › ligne 2 › cellule 1 › tableau 1 › ligne 2 › cellule 1 › paragraphe 1 › variable 1 "
                        + "#corps/2/1/0/1/1/0/0/0");
        // La ligne imbriquée ne relève pas sa propre liste : le contrôle la dit mal placée.
        assertThat(r.marqueurs().get(5).liste()).isEmpty();
        assertThat(r.marqueurs().get(3).liste()).isEqualTo("OBSERVATIONS");
    }

    @Test
    void renduBlocsLignesEtEchappement() {
        ObjectNode doc = document(paragraphe("<script>alert(1)</script> & {{INCONNUE}}"),
                paragraphe("{{SI:RESERVES}}Sous réserves{{FINSI:RESERVES}}"),
                paragraphe("Avis {{SI:RESERVES}}avec réserves{{FINSI:RESERVES}}{{SI:FAVORABLE}}favorable{{FINSI:FAVORABLE}}."),
                bloc("RESERVES", paragraphe("Annexe")),
                tableau(List.of("Point"), "OBSERVATIONS", List.of("{{OBS.POINT}}")),
                paragraphe("Obs : {{SAISI:obs}}"));
        Map<String, String> cond = Map.of("RESERVES", "avis = FAVR", "FAVORABLE", "avis = FAV");
        String fav = ModelePvPage.xhtml(doc, contexte("FAV", Map.of("obs", "ligne 1\nligne <2>")), cond, id -> null);
        assertThat(fav).contains("&lt;script&gt;alert(1)&lt;/script&gt; &amp; {{INCONNUE}}", "Avis favorable.",
                "Obs : ligne 1<br/>ligne &lt;2&gt;").doesNotContain("<script", "Sous réserves", "Annexe", "<p>&#160;</p>");
        // Une ligne par observation de l'exemple ; le paragraphe qui ne portait qu'un bloc faux a disparu.
        assertThat(fav.split("<tr>", -1)).hasSize(1 + 1 + CatalogueModelesPv.exemple().listes().get("OBSERVATIONS").size());
        String favr = ModelePvPage.xhtml(doc, contexte("FAVR", Map.of()), cond, id -> null);
        assertThat(favr).contains("Sous réserves", "Avis avec réserves.", "Annexe");
    }

    @Test
    void pdfAvecPolicesEmbarqueesEtSautDePage() throws Exception {
        ObjectNode doc = document(paragraphe("Première page {{PV.REFERENCE}}"),
                bloc("RESERVES", ModelePvPage.noeud("pageBreak"), paragraphe("ANNEXE")));
        Map<String, String> cond = Map.of("RESERVES", "avis = FAVR");
        byte[] fav = ModelePvPage.pdf(ModelePvPage.xhtml(doc, contexte("FAV", Map.of()), cond, id -> null));
        byte[] favr = ModelePvPage.pdf(ModelePvPage.xhtml(doc, contexte("FAVR", Map.of()), cond, id -> null));
        try (PDDocument a = Loader.loadPDF(fav); PDDocument b = Loader.loadPDF(favr)) {
            assertThat(a.getNumberOfPages()).isEqualTo(1);
            assertThat(b.getNumberOfPages()).isEqualTo(2);
            String texte = trie().getText(b);
            assertThat(texte).contains("Première page " + CatalogueModelesPv.exemple().variables().get("PV.REFERENCE").texte(), "ANNEXE",
                    "Page 1 / 2", "Page 2 / 2");
            PDFont police = a.getPage(0).getResources().getFont(a.getPage(0).getResources().getFontNames().iterator().next());
            assertThat(police.getName()).contains("Liberation");
        }
    }

    @Test
    void enLigneLitLesMarqueurs() {
        ArrayNode n = ModelePvPage.enLigne("A {{PV.DATE_EXAMEN|lettres}} {{SI:X}}b {{SAISI:c}}{{FINSI:X}}.", null);
        assertThat(n.size()).isEqualTo(5);
        assertThat(n.get(1).path("attrs").path("format").asString()).isEqualTo("lettres");
        assertThat(n.get(3).path("type").asString()).isEqualTo("bloc");
        assertThat(n.get(3).path("content").get(1).path("type").asString()).isEqualTo("champ");
        assertThatThrownBy(() -> ModelePvPage.valider(F.arrayNode(), id -> true)).isInstanceOf(ModelePvPage.Invalide.class);
    }

    /** Le texte dans l’ordre de la page (un texte justifié ou un compteur sinon sortent dans le désordre). */
    public static PDFTextStripper trie() throws java.io.IOException {
        PDFTextStripper s = new PDFTextStripper();
        s.setSortByPosition(true);
        return s;
    }
}
