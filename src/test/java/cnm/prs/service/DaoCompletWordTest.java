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

    @Test
    @DisplayName("⚠️ 16/10 (charte, §B1-B2, §B4) — Arial, garde et séparateurs sans en-tête ni pied, en-tête « DAO n° … », pied "
            + "« Fiche v… validée le … », article et sous-article d'une partie produite, trait d'union insécable relu « - »")
    void charte() throws Exception {
        cnm.prs.dto.FicheMarcheDto etat = etat();
        etat.setCategorie("FOURNITURES_SERVICES");
        DaoCompletService service = new DaoCompletService(null, null, null, null, null);
        java.util.Map<String, String> plan = java.util.Map.of("MINISTERE", "Ministère de l'Économie et des Finances", "ENTITE",
                "Secrétariat général", "MODE", "Appel d'offres ouvert", "FINANCEMENT", "RPI", "COMPTES", "6111");
        DaoCompletWord.Charte charte = service.charte(etat, plan, 3, java.time.LocalDateTime.of(2027, 1, 12, 9, 0));
        List<DocumentLibre.Element> el = List.of(new DocumentLibre.Paragraphe(DocumentLibre.Style.TITRE, "DONNEES PARTICULIERES D'ESSAI"),
                new DocumentLibre.Paragraphe(DocumentLibre.Style.SOUS_TITRE, "ARTICLE 1 : OBJET DU CONTRAT-CADRE"),
                new DocumentLibre.Paragraphe(DocumentLibre.Style.SOUS_TITRE, "Procédure de mise en concurrence"),
                new DocumentLibre.Paragraphe(DocumentLibre.Style.PARA, "La consultation en vue de l'attribution du contrat-cadre à un seul "
                        + "titulaire (mono-attributaire) est passée selon la procédure d'appel d'offres ouvert."),
                new DocumentLibre.Tableau(2, List.of(List.of(List.of("Etape"), List.of("Date")), List.of(List.of("Ouverture des plis"),
                        List.of("18/02/2027")))));
        byte[] dpao = new GenerateurDocumentsFiche().generer(new DocumentLibre("DPAO", null, el, "pied")).stream()
                .filter(f -> "docx".equals(f.extension())).findFirst().orElseThrow().contenu();
        DaoCompletWord word = new DaoCompletWord(true, 300, JsonMapper.builder().build());
        // §B3 — les tableaux du besoin : 40 articles sur deux lots, pour que le bordereau passe la page (en-tête répété, suite de tableau).
        TableauxBesoin.Contexte ctx = new TableauxBesoin.Contexte(false, false, "Essai", List.of(new TableauxBesoin.Lot(1, "Papeterie"),
                new TableauxBesoin.Lot(2, "Consommables informatiques")));
        List<cnm.prs.dto.ArticleBesoinDto> articles = new java.util.ArrayList<>();
        for (int i = 1; i <= 40; i++) {
            cnm.prs.dto.ArticleBesoinDto a = new cnm.prs.dto.ArticleBesoinDto();
            a.setLot(i <= 25 ? 1 : 2);
            a.setOrdre(i <= 25 ? i : i - 25);
            a.setDesignation((i <= 25 ? "Rame de papier A4 n° " : "Cartouche d'encre n° ") + i);
            a.setUnite(i <= 25 ? "Rame" : "Boîte");
            a.setQuantite(java.math.BigDecimal.valueOf(100L * i));
            a.setCaracteristiques(List.of(new cnm.prs.dto.ArticleBesoinDto.Caracteristique(null, 1, "Format", "A4"),
                    new cnm.prs.dto.ArticleBesoinDto.Caracteristique(null, 2, "Conditionnement", "carton de " + i)));
            articles.add(a);
        }
        DaoCompletWord.Resultat r = word.assembler(charte, List.of(
                new DaoCompletWord.Partie(List.of(t("PREMIÈRE PARTIE : PROCÉDURE D'APPEL D'OFFRES", 1), t("1.1. - Instructions aux candidats", 2)),
                        CharteDao.fixe(DaoCompletService.fixe("FOURNITURES_SERVICES", "IC")), true, null, "Instructions aux candidats"),
                new DaoCompletWord.Partie(List.of(t("1.2. - Données Particulières de l'Appel d'Offres (DPAO)", 2)), CharteDao.partie(dpao), true,
                        "PREMIÈRE PARTIE : PROCÉDURE D'APPEL D'OFFRES", "Données particulières de l'appel d'offres"),
                new DaoCompletWord.Partie(List.of(t("DEUXIÈME PARTIE : MARCHÉ", 1), t("2.1. - Acte d'Engagement", 2),
                        t("Annexe 1 : Cadre du bordereau des prix", 3)), TableauxBesoin.bordereau(ctx, articles), true, null, "Cadre d'acte d'engagement"),
                new DaoCompletWord.Partie(List.of(t("2.2. - Cahier des Prescriptions Spéciales", 2), t("Spécifications techniques", 3)),
                        TableauxBesoin.specifications(ctx, articles), true, "DEUXIÈME PARTIE : MARCHÉ", "Cahier des prescriptions spéciales")));
        if (System.getProperty("dao.sortie") != null) {
            java.nio.file.Files.write(java.nio.file.Path.of(System.getProperty("dao.sortie"), "dao-charte.pdf"), r.pdf());
        }
        try (PDDocument pdf = Loader.loadPDF(r.pdf())) {
            int n = pdf.getNumberOfPages();
            PDFTextStripper s = new PDFTextStripper();
            java.util.function.IntFunction<String> page = k -> {
                try {
                    s.setStartPage(k);
                    s.setEndPage(k);
                    return s.getText(pdf).replaceAll("\\s+", " ");
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            };
            assertThat(page.apply(1)).contains("MINISTÈRE DE L'ÉCONOMIE ET DES FINANCES", "SECRÉTARIAT GÉNÉRAL", "Lancé le 12/01/2027",
                    "COMPTE : 6111").doesNotContain("page 1 /", "DAO n° 001-2026");
            assertThat(page.apply(2)).contains("SOMMAIRE GÉNÉRAL", "Sommaire général", "DAO n° 001-2026", "Secrétariat général — PRMP",
                    "Fiche v3 validée le 12/01/2027", "page 2 / " + n);
            assertThat(page.apply(3)).contains("1.1. - Instructions aux candidats", "N° 001-2026").doesNotContain("page 3 /");
            String tout = s.getText(pdf);
            int dp = -1;
            for (int k = 3; k <= n; k++) {
                if (page.apply(k).contains("1.2. - Données Particulières")) {
                    dp = k;
                }
            }
            assertThat(dp).isPositive();
            assertThat(page.apply(dp)).contains("PREMIÈRE PARTIE").doesNotContain("page " + dp + " /");
            assertThat(cnm.prs.service.LectureDao.norm(page.apply(dp + 1))).contains("Données particulières de l'appel d'offres", "ARTICLE 1 : OBJET DU CONTRAT-CADRE", "(mono-attributaire)",
                    "page " + (dp + 1) + " / " + n);
            assertThat(tout).doesNotContain("Times New Roman");
            // Les polices des caractères imprimés (les espaces des lignes vides ne comptent pas).
            java.util.Set<String> polices = new java.util.TreeSet<>();
            new PDFTextStripper() {
                @Override
                protected void writeString(String texte, List<org.apache.pdfbox.text.TextPosition> ps) {
                    ps.stream().filter(x -> !x.getUnicode().isBlank()).forEach(x -> polices.add(x.getFont().getName()));
                }
            }.getText(pdf);
            // §B4 — le PDF produit se relit : tous les articles, leur lot, leur quantité et leurs caractéristiques, sans écart.
            List<LectureTableaux.Article> lus = LectureTableaux.lire("dao.pdf", r.pdf());
            assertThat(lus).hasSize(40);
            assertThat(lus).extracting(a -> a.lot).containsOnly(1, 2);
            assertThat(lus.get(24).designation).isEqualTo("Rame de papier A4 n° 25");
            assertThat(lus.get(24).quantite).isEqualByComparingTo("2500");
            assertThat(lus).allMatch(a -> a.caracteristiques.size() == 2 && a.designationSpec != null
                    && !LectureTableaux.ecart(a.designation, a.designationSpec), "articles relus : " + lus.size());
            assertThat(polices).allMatch(x -> x.contains("Arial") || x.contains("Symbol") || x.contains("Wingdings") || x.contains("Math"), "polices : " + polices);
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
