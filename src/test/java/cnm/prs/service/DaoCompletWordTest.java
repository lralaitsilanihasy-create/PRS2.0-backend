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
                new DaoCompletService(null, null, null, null, null).garde(etat(), java.util.Map.of("MINISTERE",
                        "Ministère de l'Éducation nationale", "MODE", "Appel d'offres ouvert", "LOTS_DESIGNATION", "Ordinateurs ; Imprimantes",
                        "FINANCEMENT", "RPI", "COMPTES", "2321")),
                "SOMMAIRE", "DAO n° 001-2026 — Essai",
                List.of(new DaoCompletWord.Partie(List.of(t("PREMIÈRE PARTIE : PROCÉDURE D'APPEL D'OFFRES", 1),
                        t("1.1. - Instructions aux candidats", 2)), DaoCompletService.fixe("TRAVAUX", "IC")),
                        new DaoCompletWord.Partie(List.of(t("1.2. - Données Particulières de l'Appel d'Offres (DPAO)", 2)),
                                docx("Données particulières d'essai")),
                        new DaoCompletWord.Partie(List.of(), docx("Suite de la section II")),
                        new DaoCompletWord.Partie(List.of(t("1.3. - Formulaires de soumission", 2), t("A. - Modèles de fiches de renseignements", 3),
                                t("A1 - Identification du Candidat", 4)), docx("Fiche A1")),
                        new DaoCompletWord.Partie(List.of(t("DEUXIÈME PARTIE : MARCHÉ", 1), t("2.1. - Acte d'Engagement", 2),
                                t("Lot 1", 3)), null),
                        new DaoCompletWord.Partie(List.of(t("2.3. - Cahier des Clauses Administratives Générales", 2)),
                                DaoCompletService.fixe("TRAVAUX", "CCAG"))));
        assertThat(new String(r.docx(), 0, 2)).isEqualTo("PK");
        if (System.getProperty("dao.sortie") != null) {   // relecture à l'œil : -Ddao.sortie=<dossier>
            java.nio.file.Files.write(java.nio.file.Path.of(System.getProperty("dao.sortie"), "dao-complet.pdf"), r.pdf());
        }
        try (PDDocument pdf = Loader.loadPDF(r.pdf())) {
            int n = pdf.getNumberOfPages();
            assertThat(n).isGreaterThan(50);
            PDFTextStripper s = new PDFTextStripper();
            s.setStartPage(2);
            s.setEndPage(2);
            String sommaire = s.getText(pdf).replaceAll("\\s+", " ");
            assertThat(sommaire).contains("SOMMAIRE", "DAO n° 001-2026", "page 2 / " + n, "PREMIÈRE PARTIE", "1.2. - Données",
                    "DEUXIÈME PARTIE", "Lot 1", "2.3. - Cahier", "A1 - Identification du Candidat").doesNotContain("Suite de la section II", "DOSSIER TYPE");
            // ⚠️ C2 — les couvertures des documents types ne sont plus recopiées.
            assertThat(new PDFTextStripper().getText(pdf)).doesNotContain("DOSSIER TYPE D'APPEL D'OFFRES", "REPUBLIQUE DE MADAGASCAR");
            s.setStartPage(n);
            s.setEndPage(n);
            assertThat(s.getText(pdf)).contains("page " + n + " / " + n);
        }
    }

    private static cnm.prs.dto.FicheMarcheDto etat() {
        cnm.prs.dto.FicheMarcheDto e = new cnm.prs.dto.FicheMarcheDto();
        e.setCategorie("TRAVAUX");
        e.setValeurs(java.util.Map.of("B02-OB-03", "001-2026"));
        e.setDesignationMarche("Essai");
        return e;
    }

    private static DaoCompletWord.Titre t(String texte, int niveau) {
        return new DaoCompletWord.Titre(texte, niveau);
    }

    private static byte[] docx(String texte) throws Exception {
        try (XWPFDocument d = new XWPFDocument(); ByteArrayOutputStream o = new ByteArrayOutputStream()) {
            d.createParagraph().createRun().setText(texte);
            d.write(o);
            return o.toByteArray();
        }
    }
}
