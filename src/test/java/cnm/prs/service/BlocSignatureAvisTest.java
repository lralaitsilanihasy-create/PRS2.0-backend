package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.service.DocumentLibre.Paragraphe;
import cnm.prs.service.DocumentLibre.Style;

/**
 * ⚠️ 2026-10-01 (avis spécifique, contre-recette du front) — le bloc de signature de l'avis (« à …, le … », la qualité,
 * le nom) gardé ensemble : le nom de la PRMP sortait seul en page 2. Pur : ni base, ni Spring.
 */
class BlocSignatureAvisTest {

    private static final String LIEU = "à Antananarivo, le 05/10/2026";
    private static final String QUALITE = "La Personne Responsable des Marchés Publics";
    private static final String NOM = "RANDRIANARIVO La Personne";

    @Test
    @DisplayName("finGardeeEnsemble(3) : les deux premiers des trois derniers paragraphes imprimés sont solidaires du suivant, "
            + "les paragraphes vides de fin ne comptent pas, le reste est inchangé")
    void marque() {
        DocumentLibre d = document(5, true).finGardeeEnsemble(3);
        List<DocumentLibre.Element> e = d.elements();
        assertThat(e).hasSize(9);
        assertThat(e.subList(0, 5)).allMatch(x -> !((Paragraphe) x).solidaireDuSuivant());
        assertThat(e.subList(5, 9)).extracting(x -> ((Paragraphe) x).texte() + ":" + ((Paragraphe) x).solidaireDuSuivant())
                .containsExactly(LIEU + ":true", QUALITE + ":true", NOM + ":false", ":false");
    }

    @Test
    @DisplayName("Word : « paragraphe solidaire du suivant » (keepNext) et lignes non séparées sur le lieu et la qualité, "
            + "pas sur le nom")
    void keepNextDansLeWord() throws Exception {
        byte[] docx = new GenerateurDocumentsFiche().generer(document(5, false).finGardeeEnsemble(3)).get(0).contenu();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            List<String> solidaires = new ArrayList<>();
            for (XWPFParagraph p : doc.getParagraphs()) {
                if (p.getCTP().isSetPPr() && p.getCTP().getPPr().isSetKeepNext()) {
                    assertThat(p.getCTP().getPPr().isSetKeepLines()).isTrue();
                    solidaires.add(p.getText());
                }
            }
            assertThat(solidaires).containsExactly(LIEU, QUALITE);
        }
    }

    @Test
    @DisplayName("PDF : quelle que soit la longueur du corps, le lieu, la qualité et le nom sont sur la même page ; sans la "
            + "règle, au moins une longueur sépare le nom (le test mord)")
    void blocIndivisibleDansLePdf() throws Exception {
        GenerateurDocumentsFiche g = new GenerateurDocumentsFiche();
        boolean separeSansRegle = false;
        for (int n = 30; n <= 80; n++) {
            DocumentLibre d = document(n, false);
            assertThat(pages(g.generer(d.finGardeeEnsemble(3)).get(1).contenu())).as("n = " + n).hasSize(1);
            separeSansRegle |= pages(g.generer(d).get(1).contenu()).size() > 1;
        }
        assertThat(separeSansRegle).isTrue();
    }

    /** Les pages où paraissent le lieu, la qualité et le nom. */
    private static java.util.Set<Integer> pages(byte[] pdf) throws Exception {
        java.util.Set<Integer> pages = new java.util.TreeSet<>();
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            for (int i = 1; i <= doc.getNumberOfPages(); i++) {
                org.apache.pdfbox.text.PDFTextStripper s = new org.apache.pdfbox.text.PDFTextStripper();
                s.setStartPage(i);
                s.setEndPage(i);
                String t = s.getText(doc);
                for (String ligne : List.of(LIEU, QUALITE, NOM)) {
                    if (t.contains(ligne)) {
                        pages.add(i);
                    }
                }
            }
        }
        return pages;
    }

    /** {@code n} paragraphes de corps, puis le bloc de signature (et un paragraphe vide de fin si demandé). */
    private static DocumentLibre document(int n, boolean videDeFin) {
        List<DocumentLibre.Element> e = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            e.add(new Paragraphe(Style.PARA, i + ". Les candidats intéressés peuvent obtenir des informations auprès de "
                    + "l'Unité de Gestion de la Passation des Marchés."));
        }
        e.add(new Paragraphe(Style.PARA, LIEU));
        e.add(new Paragraphe(Style.PARA, QUALITE));
        e.add(new Paragraphe(Style.PARA, NOM));
        if (videDeFin) {
            e.add(new Paragraphe(Style.VIDE, ""));
        }
        return new DocumentLibre("AVIS", null, e, "pied");
    }
}
