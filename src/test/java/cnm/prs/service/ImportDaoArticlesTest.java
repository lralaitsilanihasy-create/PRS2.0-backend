package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.dto.FicheMarcheDto;
import cnm.prs.dto.ImportDaoResult;

/**
 * ⚠️ 2026-10-16 (« import-specifications », §B5, H1-H2) — les articles lus rapportés au plan et à la forme de la fiche, et la lecture
 * d'un Word (§B4), sans les DAO réels.
 */
class ImportDaoArticlesTest {

    private static FicheMarcheDto fiche(String type, int nbLots) {
        FicheMarcheDto f = new FicheMarcheDto();
        f.setTypeMarche(type);
        f.setNbLots(nbLots);
        return f;
    }

    private static LectureTableaux.Article lu(Integer lot, String code, String designation, String spec, String qte, String min, String max) {
        LectureTableaux.Article a = new LectureTableaux.Article();
        a.lot = lot;
        a.code = code;
        a.n = Integer.parseInt(code);
        a.designation = designation;
        a.designationSpec = spec;
        a.bordereau = designation != null;
        a.unite = "Unité";
        a.quantite = qte == null ? null : new BigDecimal(qte);
        a.min = min == null ? null : new BigDecimal(min);
        a.max = max == null ? null : new BigDecimal(max);
        a.caracteristiques = List.of(new LectureTableaux.Caracteristique("Mémoire vive", "8 Go"),
                new LectureTableaux.Caracteristique("De bonne qualité", ""));
        return a;
    }

    @Test
    @DisplayName("Ligne non allotie : sans lot (H1), lot 2 lu hors plan ; à commande : une seule quantité incompatible ; écart et "
            + "spécification sans article signalés ; caractéristique sans libellé : son texte en exigence (H2)")
    void nonAllotieACommande() {
        List<String> avertissements = new ArrayList<>();
        List<ImportDaoResult.Article> out = ImportDaoService.articles(fiche("A_COMMANDE", 1), true, List.of(
                lu(1, "1", "Bombe air comprimé 500ml", "Bombe air comprimé 100ml", null, "10", "20"),
                lu(2, "2", "Crème à récurer", "Crème à récurer", "5", null, null),
                lu(null, "3", null, "Gants de ménage", null, null, null)), avertissements);
        assertThat(out.get(0).lot()).isNull();
        assertThat(out.get(0).anomalies()).isEmpty();
        assertThat(out.get(0).designationSpecification()).isEqualTo("Bombe air comprimé 100ml");
        assertThat(out.get(0).caracteristiques()).extracting(ImportDaoResult.Caracteristique::libelle).containsExactly("Mémoire vive", "Spécification");
        assertThat(out.get(0).caracteristiques().get(1).exigence()).isEqualTo("De bonne qualité");
        assertThat(out.get(0).confiance()).isEqualTo(new ImportDaoResult.Confiance("haute", "moyenne"));
        assertThat(out.get(1).anomalies()).hasSize(2).anyMatch(s -> s.startsWith("LOT_HORS_PLAN")).anyMatch(s -> s.startsWith("QUANTITES_INCOMPATIBLES"));
        assertThat(out.get(2).designation()).isEqualTo("Gants de ménage");
        assertThat(out.get(2).confiance().article()).isEqualTo("moyenne");
        assertThat(avertissements).anyMatch(s -> s.contains("« Bombe air comprimé 500ml », les spécifications « Bombe air comprimé 100ml »"))
                .anyMatch(s -> s.contains("n° 3") && s.contains("sans article du bordereau"));
    }

    @Test
    @DisplayName("Ligne allotie : le lot lu dans le plan est gardé, le lot absent ou hors plan est une anomalie ; contrat-cadre : min/max "
            + "incompatibles, quantités indicatives signalées")
    void allotieContratCadre() {
        List<String> avertissements = new ArrayList<>();
        List<ImportDaoResult.Article> out = ImportDaoService.articles(fiche("CONTRAT_CADRE", 3), false, List.of(
                lu(2, "1", "Ordinateur", "Ordinateur", "1", null, null), lu(null, "2", "Souris", "Souris", "1", null, null),
                lu(4, "3", "Clavier", "Clavier", null, "1", "2")), avertissements);
        assertThat(out.get(0).lot()).isEqualTo(2);
        assertThat(out.get(0).anomalies()).isEmpty();
        assertThat(out.get(0).pages()).isNull();
        assertThat(out.get(1).anomalies()).singleElement().asString().startsWith("LOT_HORS_PLAN");
        assertThat(out.get(2).anomalies()).hasSize(2);
        assertThat(avertissements).contains("Contrat-cadre : les quantités du bordereau sont données à titre indicatif.");
    }

    private static void tableau(org.apache.poi.xwpf.usermodel.XWPFDocument doc, String[]... lignes) {
        org.apache.poi.xwpf.usermodel.XWPFTable t = doc.createTable(lignes.length, lignes[0].length);
        for (int i = 0; i < lignes.length; i++) {
            for (int j = 0; j < lignes[i].length; j++) {
                t.getRow(i).getCell(j).setText(lignes[i][j]);
            }
        }
    }

    @Test
    @DisplayName("§B4 — un Word : les tableaux natifs, le lot au-dessus, bordereau à min/max et spécifications rapprochés par numéro")
    void word() throws Exception {
        java.io.ByteArrayOutputStream sortie = new java.io.ByteArrayOutputStream();
        try (org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument()) {
            doc.createParagraph().createRun().setText("LOT N°2 : Matériel informatique");
            tableau(doc, new String[] { "N°", "Désignation", "Unité", "Min", "Max" }, new String[] { "001", "Ordinateur portable, l'unité à…", "Unité", "5", "10" },
                    new String[] { "002", "Imprimante laser", "Unité", "2", "4" });
            doc.createParagraph().createRun().setText("Spécifications techniques");
            tableau(doc, new String[] { "N°", "Désignation", "Spécifications minimales", "Proposées par le candidat" },
                    new String[] { "1", "Ordinateur portable", "Mémoire vive : 8 Go", "" }, new String[] { "2", "Imprimante laser", "Recto verso", "" });
            doc.write(sortie);
        }
        List<LectureTableaux.Article> lus = LectureTableaux.lire("dao.docx", sortie.toByteArray());
        assertThat(lus).extracting(a -> a.lot).containsExactly(2, 2);
        assertThat(lus).extracting(a -> a.code).containsExactly("001", "002");
        assertThat(lus.get(0).designation).isEqualTo("Ordinateur portable");
        assertThat(lus.get(0).min).isEqualByComparingTo("5");
        assertThat(lus.get(0).max).isEqualByComparingTo("10");
        assertThat(lus.get(0).caracteristiques).containsExactly(new LectureTableaux.Caracteristique("Mémoire vive", "8 Go"));
        assertThat(lus.get(1).designationSpec).isEqualTo("Imprimante laser");
    }

    @Test
    @DisplayName("Règle 11 : les nombres ne se comparent que présents des deux côtés ; les abréviations et les compléments ne sont pas des écarts")
    void ecarts() {
        assertThat(LectureTableaux.ecart("Bombe air comprimé 500ml", "Bombe air comprimé 100ml")).isTrue();
        assertThat(LectureTableaux.ecart("Béton B2 dosé à 250kg/m3 de ciment", "Béton B1 dosé à 250 kg/m3 de ciment")).isTrue();
        assertThat(LectureTableaux.ecart("Ordinateurs portables", "Ordinateur portable I3")).isFalse();
        assertThat(LectureTableaux.ecart("Fourniture et pose transmission AV. G/D", "Transmission AV. G/D")).isFalse();
    }

    @Test
    @DisplayName("Caractéristiques : « Libellé : valeur », découpage d'un texte d'un seul tenant, lignes prolongées par un mot-outil")
    void caracteristiques() {
        assertThat(LectureTableaux.caracteristiques(List.of("Plastique solide durable, 30L avec couvercle", "pivotant, Dim:625×576"), true))
                .extracting(LectureTableaux.Caracteristique::libelle).containsExactly("Plastique solide durable", "30L avec couvercle pivotant", "Dim");
        assertThat(LectureTableaux.caracteristiques(List.of("Mémoire vive : 8 Go au minimum", "Processeur Core i5 ou", "équivalent"), false))
                .containsExactly(new LectureTableaux.Caracteristique("Mémoire vive", "8 Go au minimum"),
                        new LectureTableaux.Caracteristique("Processeur Core i5 ou équivalent", ""));
        assertThat(LectureTableaux.nettoie("Bac à ordure métallique, l’unité à........ ....(en lettre)")).isEqualTo("Bac à ordure métallique");
    }
}
