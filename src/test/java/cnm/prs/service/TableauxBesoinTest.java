package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.dto.ArticleBesoinDto;

/**
 * ⚠️ 2026-10-16 (charte, §B3-B4) — les tableaux du besoin : colonnes au mot près, désignation au bordereau, un lot par page, et la
 * relecture par l'import (le Word produit rend ses articles).
 */
class TableauxBesoinTest {

    private static ArticleBesoinDto article(Integer lot, int ordre, String designation, String unite, String qte, String min, String max,
            String... caracs) {
        ArticleBesoinDto a = new ArticleBesoinDto();
        a.setLot(lot);
        a.setOrdre(ordre);
        a.setDesignation(designation);
        a.setUnite(unite);
        a.setQuantite(qte == null ? null : new BigDecimal(qte));
        a.setQuantiteMin(min == null ? null : new BigDecimal(min));
        a.setQuantiteMax(max == null ? null : new BigDecimal(max));
        List<ArticleBesoinDto.Caracteristique> ks = new java.util.ArrayList<>();
        for (int i = 0; i + 1 < caracs.length; i += 2) {
            ks.add(new ArticleBesoinDto.Caracteristique(null, i / 2 + 1, caracs[i], caracs[i + 1]));
        }
        a.setCaracteristiques(ks);
        return a;
    }

    private static String texte(byte[] docx) throws Exception {
        try (XWPFDocument d = new XWPFDocument(new ByteArrayInputStream(docx)); XWPFWordExtractor x = new XWPFWordExtractor(d)) {
            return x.getText();
        }
    }

    @Test
    @DisplayName("L'article défini de l'unité : « la rame », « le paquet », « l'unité » (défaut et voyelle), abréviations des travaux")
    void unites() {
        assertThat(TableauxBesoin.designationPrix("Rame de papier A4 80 g", "Rame", null)).isEqualTo("Rame de papier A4 80 g, la rame à ….Ariary");
        assertThat(TableauxBesoin.uniteAvecArticle("Paquet")).isEqualTo("le paquet");
        assertThat(TableauxBesoin.uniteAvecArticle("Boîte")).isEqualTo("la boîte");
        assertThat(TableauxBesoin.uniteAvecArticle("U")).isEqualTo("l'unité");
        assertThat(TableauxBesoin.uniteAvecArticle("")).isEqualTo("l'unité");
        assertThat(TableauxBesoin.uniteAvecArticle("Ensemble")).isEqualTo("l'ensemble");
        assertThat(TableauxBesoin.uniteAvecArticle("M3")).isEqualTo("le mètre cube");
        assertThat(TableauxBesoin.uniteAvecArticle("Fft")).isEqualTo("le forfait");
        assertThat(TableauxBesoin.designationPrix("Béton B1", "M3", "Le mètre cube")).isEqualTo("Béton B1, le mètre cube à ….Ariary");
        // La relecture retire ce suffixe (règle 7 de l'import).
        assertThat(LectureTableaux.nettoie("Rame de papier A4 80 g, la rame à ….Ariary")).isEqualTo("Rame de papier A4 80 g");
        assertThat(LectureTableaux.nettoie("Agrafeuse, l'unité à ….Ariary")).isEqualTo("Agrafeuse");
    }

    @Test
    @DisplayName("Ligne allotie à commande : un tableau par lot sous « Lot k : … », MIN et MAX, colonnes au mot près ; relu par l'import")
    void allotieACommande() throws Exception {
        TableauxBesoin.Contexte c = new TableauxBesoin.Contexte(true, false, "Matériels informatiques",
                List.of(new TableauxBesoin.Lot(1, "Ordinateurs portables"), new TableauxBesoin.Lot(2, "Imprimantes")));
        List<ArticleBesoinDto> as = List.of(article(1, 1, "Ordinateur portable I3", "U", null, "100", "300", "Mémoire vive", "8 Go au minimum",
                "Processeur", "Core i3"), article(1, 2, "Ordinateur portable I5", "U", null, "40", "120", "Mémoire vive", "16 Go"),
                article(2, 1, "Imprimante laser", "U", null, "5", "17", "Vitesse", "30 pages par minute"));
        byte[] bordereau = TableauxBesoin.bordereau(c, as);
        assertThat(texte(bordereau)).contains("ANNEXE 1 — CADRE DU BORDEREAU DES PRIX", "Lot 1 : Ordinateurs portables", "Lot 2 : Imprimantes",
                "DESIGNATION DES ARTICLES ET PRIX UNITAIRE EN TOUTES LETTRES", "MIN", "MAX", "Ordinateur portable I3, l'unité à ….Ariary",
                "Montant de l'offre — lot 1", "Arrêté le montant du marché").doesNotContain("QUANTITE");
        byte[] specs = TableauxBesoin.specifications(c, as);
        assertThat(texte(specs)).contains("SPECIFICATIONS MINIMUMS REQUISES", "PROPOSITIONS DU CANDIDAT", "Mémoire vive : 8 Go au minimum");
        // §B4 — la relecture par les tableaux : chaque article, son lot, ses quantités, ses caractéristiques.
        List<LectureTableaux.Article> lus = LectureTableaux.articles(new java.util.ArrayList<>(java.util.stream.Stream.concat(
                LectureTableaux.tableauxDocx(bordereau).stream(), LectureTableaux.tableauxDocx(specs).stream()).toList()));
        assertThat(lus).extracting(a -> a.lot + "#" + a.code + " " + a.designation + " " + a.min + "/" + a.max)
                .containsExactly("1#1 Ordinateur portable I3 100/300", "1#2 Ordinateur portable I5 40/120", "2#1 Imprimante laser 5/17");
        assertThat(lus.get(0).caracteristiques).containsExactly(new LectureTableaux.Caracteristique("Mémoire vive", "8 Go au minimum"),
                new LectureTableaux.Caracteristique("Processeur", "Core i3"));
        assertThat(lus).allMatch(a -> a.designationSpec != null && !LectureTableaux.ecart(a.designation, a.designationSpec));
    }

    @Test
    @DisplayName("⚠️ R5 (17/10) — le Normal du tableau est justifié et ses cellules alignées à gauche : Word garde alors l'alignement à "
            + "l'insertion ; les cellules d'un formulaire de modèle aussi, avec les marges intérieures")
    void alignementGardeALInsertion() throws Exception {
        TableauxBesoin.Contexte c = new TableauxBesoin.Contexte(false, false, "x", List.of(new TableauxBesoin.Lot(null, null)));
        byte[] b = TableauxBesoin.specifications(c, List.of(article(null, 1, "Rame", "Rame", "1", null, null)));
        try (XWPFDocument d = new XWPFDocument(new ByteArrayInputStream(b))) {
            assertThat(d.getStyles().getStyle("Normal").getCTStyle().getPPr().getJc().getVal().toString()).isEqualTo("both");
            assertThat(d.getTables().get(0).getRow(0).getCell(3).getParagraphs().get(0).getAlignment())
                    .isEqualTo(org.apache.poi.xwpf.usermodel.ParagraphAlignment.LEFT);
        }
        List<DocumentLibre.Element> el = List.of(new DocumentLibre.Tableau(2, List.of(List.of(List.of("Nom de la société"), List.of("x")))));
        byte[] f = new GenerateurDocumentsFiche().generer(new DocumentLibre("A1", null, el, "p")).stream()
                .filter(x -> "docx".equals(x.extension())).findFirst().orElseThrow().contenu();
        try (XWPFDocument d = new XWPFDocument(new ByteArrayInputStream(CharteDao.partie(f)))) {
            assertThat(d.getTables().get(0).getRow(0).getCell(0).getParagraphs().get(0).getAlignment())
                    .isEqualTo(org.apache.poi.xwpf.usermodel.ParagraphAlignment.LEFT);
            assertThat(d.getTables().get(0).getCTTbl().getTblPr().getTblCellMar().getLeft().getW().toString()).isEqualTo("85");
        }
    }

    @Test
    @DisplayName("⚠️ R6 (17/10) — « No. d'appel d'offres » des formulaires : en contrat-cadre, B02-OE-01 (le numéro du DPAC)")
    void numeroDuContratCadre() {
        cnm.prs.dto.FicheMarcheDto cc = new cnm.prs.dto.FicheMarcheDto();
        cc.setTypeMarche("CONTRAT_CADRE");
        cc.setValeurs(Map.of("B02-OE-01", "07-CC/MEF/SG/PRMP.2027"));
        assertThat(FormulairesCandidat.resolveur(cc, Map.of(), null).apply("B02-OB-03")).isEqualTo("07-CC/MEF/SG/PRMP.2027");
        cnm.prs.dto.FicheMarcheDto qf = new cnm.prs.dto.FicheMarcheDto();
        qf.setTypeMarche("QUANTITE_FIXE");
        qf.setValeurs(Map.of("B02-OE-01", "ignoré"));
        assertThat(FormulairesCandidat.resolveur(qf, Map.of(), null).apply("B02-OB-03")).isNotEqualTo("ignoré");
    }

    @Test
    @DisplayName("Contrat-cadre non alloti : l'objet en titre, quantités indicatives ; liste à délai fixé par l'autorité ; besoin vide : rien")
    void contratCadre() throws Exception {
        TableauxBesoin.Contexte c = new TableauxBesoin.Contexte(false, true, "Fourniture d'articles de bureau", List.of(new TableauxBesoin.Lot(null, null)));
        List<ArticleBesoinDto> as = List.of(article(null, 1, "Rame de papier A4 80 g", "Rame", "3000", null, null, "Format", "A4"));
        assertThat(texte(TableauxBesoin.bordereau(c, as))).contains("Fourniture d'articles de bureau", "QUANTITE", "3 000",
                "Rame de papier A4 80 g, la rame à ….Ariary", "Les quantités sont indicatives").doesNotContain("Lot 1");
        assertThat(texte(TableauxBesoin.liste(c, as, Map.of("B07-DE-02", "15 jours après le bon de commande"))))
                .contains("LISTE DES FOURNITURES ET CALENDRIER DE LIVRAISON", "Fixée par chaque marché subséquent", "15 jours après le bon de commande");
        assertThat(TableauxBesoin.bordereau(c, List.of())).isNull();
    }

    @Test
    @DisplayName("Travaux : bordereau des prix unitaires sans quantité, puis DQE avec séries et totaux ; seul le DQE se relit comme bordereau")
    void travaux() throws Exception {
        TableauxBesoin.Contexte c = new TableauxBesoin.Contexte(false, false, "Entretien routier", List.of(new TableauxBesoin.Lot(null, null)));
        ArticleBesoinDto a = article(null, 1, "Installation et repli de chantier", "Fft", "1", null, null);
        a.setNumeroPrix("001");
        a.setSerie("000");
        a.setSerieLibelle("Installation de chantier");
        ArticleBesoinDto b = article(null, 2, "Béton B1 dosé à 250 kg/m³", "M3", "140", null, null);
        b.setNumeroPrix("531");
        b.setSerie("500");
        b.setSerieLibelle("Ouvrages");
        byte[] bpu = TableauxBesoin.bpu(c, List.of(a, b));
        byte[] dqe = TableauxBesoin.dqe(c, List.of(a, b), new BigDecimal("20"));
        // ⚠️ 17/10 (T3) — le prix en lettres sur sa propre ligne, sous la désignation.
        assertThat(texte(bpu)).contains("PRIX UNITAIRE EN CHIFFRES", "Installation et repli de chantier\tLe forfait à : …………… Ariary",
                "INSTALLATION DE CHANTIER").doesNotContain("QUANTITE");
        // ⚠️ 17/10 (T4) — un sous-total après chaque série, puis la récapitulation du lot.
        assertThat(texte(dqe)).contains("DESIGNATION DES OUVRAGES", "Sous-total 000 — INSTALLATION DE CHANTIER", "Sous-total 500 — OUVRAGES",
                "RECAPITULATION", "SERIE\tDESIGNATION\tMONTANT EN ARIARY", "Total hors taxes", "TVA 20 %", "Total toutes taxes comprises",
                "Arrêté le montant du marché à la somme de");
        assertThat(LectureTableaux.nettoie("Béton B1 dosé à 250 kg Le mètre cube à : …………… Ariary")).isEqualTo("Béton B1 dosé à 250 kg");
        assertThat(TableauxBesoin.lignePrix("M3", "Le mètre cube")).isEqualTo("Le mètre cube à : …………… Ariary");
        assertThat(TableauxBesoin.lignePrix("Fft", null)).isEqualTo("Le forfait à : …………… Ariary");
        assertThat(LectureTableaux.articles(LectureTableaux.tableauxDocx(bpu))).isEmpty();
        List<LectureTableaux.Article> lus = LectureTableaux.articles(LectureTableaux.tableauxDocx(dqe));
        assertThat(lus).extracting(x -> x.code + " " + x.serie + " " + x.quantite).containsExactly("001 000 1", "531 500 140");   // sous-totaux exclus
        TableauxBesoin.Contexte f = new TableauxBesoin.Contexte(false, false, "Entretien routier", List.of(new TableauxBesoin.Lot(1, "Voirie"),
                new TableauxBesoin.Lot(2, "Assainissement")), true);
        a.setLot(2);
        b.setLot(2);
        assertThat(texte(TableauxBesoin.dqe(f, List.of(a, b), null))).contains("RECAPITULATION — LOT 2", "Arrêté le montant du lot 2");
    }
}
