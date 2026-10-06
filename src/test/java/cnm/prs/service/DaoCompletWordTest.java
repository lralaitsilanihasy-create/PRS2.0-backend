package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/**
 * ⚠️ 2026-10-06 (DAO complet) — l'assemblage <strong>par Word</strong> : page de garde, sommaire limité aux parties avec leurs
 * numéros de page, en-tête, « page n / N » continu, textes fixes de l'ARMP insérés. Marqué {@code word} : exclu de la CI (pas de
 * Word sur les runners Linux), exécuté en local.
 */
@Tag("word")
class DaoCompletWordTest {

    @Test
    @DisplayName("Word assemble garde, sommaire, parties (IC, une partie produite, CCAG), en-tête et pagination ; .docx et .pdf")
    void assembler() throws Exception {
        DaoCompletWord word = new DaoCompletWord(true, 300, JsonMapper.builder().build());
        DaoCompletWord.Resultat r = word.assembler(
                List.of(new DaoCompletWord.LigneGarde("MINISTÈRE DE L'ÉDUCATION NATIONALE", 13, true),
                        new DaoCompletWord.LigneGarde("DOSSIER D'APPEL D'OFFRES", 22, true)),
                "SOMMAIRE", "DAO n° 001-2026 — Essai",
                List.of(new DaoCompletWord.Partie("Section I — Instructions aux candidats", DaoCompletService.fixe("TRAVAUX", "IC")),
                        new DaoCompletWord.Partie("Section II — Données particulières de l'appel d'offres", docx("Données particulières d'essai")),
                        new DaoCompletWord.Partie(null, docx("Suite de la section II")),
                        new DaoCompletWord.Partie("Section VI — Cahier des clauses administratives générales", DaoCompletService.fixe("TRAVAUX", "CCAG"))));
        assertThat(new String(r.docx(), 0, 2)).isEqualTo("PK");
        try (PDDocument pdf = Loader.loadPDF(r.pdf())) {
            int n = pdf.getNumberOfPages();
            assertThat(n).isGreaterThan(50);
            PDFTextStripper s = new PDFTextStripper();
            s.setStartPage(2);
            s.setEndPage(2);
            String sommaire = s.getText(pdf).replaceAll("\\s+", " ");
            assertThat(sommaire).contains("SOMMAIRE", "DAO n° 001-2026", "page 2 / " + n).containsIgnoringCase("Section II")
                    .containsIgnoringCase("Section VI").doesNotContain("Suite de la section II");
            s.setStartPage(n);
            s.setEndPage(n);
            assertThat(s.getText(pdf)).contains("page " + n + " / " + n);
        }
    }

    private static byte[] docx(String texte) throws Exception {
        try (XWPFDocument d = new XWPFDocument(); ByteArrayOutputStream o = new ByteArrayOutputStream()) {
            d.createParagraph().createRun().setText(texte);
            d.write(o);
            return o.toByteArray();
        }
    }
}
