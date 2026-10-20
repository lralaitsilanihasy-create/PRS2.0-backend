package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

/**
 * ⚠️ 2026-10-20 (« generateur-modeles-pv », lot G1) — le moteur des modèles de PV sur des Word construits par POI : marqueurs coupés
 * par Word, blocs conditionnels, lignes répétées, anciens marqueurs, formats, défauts de structure.
 */
public class ModelePvWordTest {

    /** Un Word dont chaque paragraphe est une liste de morceaux (runs), le premier en gras pour vérifier qu'il le reste. */
    public static byte[] docx(Consumer<XWPFDocument> corps) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            corps.accept(doc);
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void paragraphe(XWPFDocument doc, String... morceaux) {
        XWPFParagraph p = doc.createParagraph();
        for (String m : morceaux) {
            p.createRun().setText(m);
        }
    }

    private static ModelePvWord.Contexte ctx(String avis) {
        Map<String, ModelePvWord.Valeur> v = new LinkedHashMap<>();
        v.put("PV.REFERENCE", ModelePvWord.Valeur.de("00001/MAOO-CC/CNM/PV/2026"));
        v.put("PV.DATE_EXAMEN", ModelePvWord.Valeur.date(LocalDate.of(2026, 10, 22)));
        v.put("COMMISSION.CHEF_LIEU", ModelePvWord.Valeur.de("Antananarivo"));
        v.put("PROCEDURE.OBJET", ModelePvWord.Valeur.de("Fourniture\nde produits"));
        Map<String, ModelePvWord.Valeur> t1 = Map.of("TIT.RANG", ModelePvWord.Valeur.de("1"), "TIT.CANDIDAT", ModelePvWord.Valeur.de("BTP Alpha"));
        Map<String, ModelePvWord.Valeur> t2 = Map.of("TIT.RANG", ModelePvWord.Valeur.de("2"), "TIT.CANDIDAT", ModelePvWord.Valeur.de("BTP Gamma"));
        return new ModelePvWord.Contexte(v, Map.of("TITULAIRES", List.of(t1, t2)), Map.of("avis", avis), Map.of("motif", "prix anormal"));
    }

    private static final Map<String, String> CONDITIONS = Map.of("AVEC_RESERVES", "avis = FAVR", "DEFAVORABLE", "avis = DEF");

    @Test
    void unMarqueurCoupeParWordEstRecolleEtGardeSaMiseEnForme() throws Exception {
        byte[] modele = docx(doc -> {
            XWPFParagraph p = doc.createParagraph();
            XWPFRun gras = p.createRun();
            gras.setBold(true);
            gras.setText("PV n° {{PV.");
            p.createRun().setText("REFER");
            p.createRun().setText("ENCE}} du {{PV.DATE_EXAMEN|lettres}}");
        });
        byte[] rempli = ModelePvWord.remplir(modele, ctx("FAV"), CONDITIONS);
        assertThat(ModelePvWord.texte(rempli)).isEqualTo("PV n° 00001/MAOO-CC/CNM/PV/2026 du deux mille vingt-six et le vingt-deux octobre");
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(rempli))) {
            XWPFRun premier = doc.getParagraphs().get(0).getRuns().get(0);
            assertThat(premier.isBold()).isTrue();
            assertThat(premier.getText(0)).isEqualTo("PV n° 00001/MAOO-CC/CNM/PV/2026");
        }
        assertThat(ModelePvWord.relever(modele).defauts()).isEmpty();
    }

    @Test
    void lesBlocsSuiventLeursConditionsDansUnParagrapheEtSurPlusieurs() throws Exception {
        byte[] modele = docx(doc -> {
            paragraphe(doc, "La Commission émet un avis favorable{{SI:AVEC_RESERVES}} sous réserve des observations{{FINSI:AVEC_RESERVES}}.");
            paragraphe(doc, "{{SI:DEFAVORABLE}}");
            paragraphe(doc, "Motif : {{SAISI:motif}}");
            XWPFTable t = doc.createTable(1, 1);
            t.getRow(0).getCell(0).setText("Tableau du refus");
            paragraphe(doc, "{{FINSI:DEFAVORABLE}}");
            paragraphe(doc, "Fait à {{COMMISSION.CHEF_LIEU}}");
        });
        String favr = ModelePvWord.texte(ModelePvWord.remplir(modele, ctx("FAVR"), CONDITIONS));
        assertThat(favr).isEqualTo("La Commission émet un avis favorable sous réserve des observations.\nFait à Antananarivo");
        String def = ModelePvWord.texte(ModelePvWord.remplir(modele, ctx("DEF"), CONDITIONS));
        assertThat(def).contains("avis favorable.", "Motif : prix anormal", "Tableau du refus", "Fait à Antananarivo")
                .doesNotContain("{{", "sous réserve");
    }

    @Test
    void uneLigneDeTableauSeRepeteParTitulaireEtUneValeurSurPlusieursLignesDevientDesSauts() throws Exception {
        byte[] modele = docx(doc -> {
            paragraphe(doc, "Objet : {{PROCEDURE.OBJET|majuscules}}");
            XWPFTable t = doc.createTable(2, 2);
            t.getRow(0).getCell(0).setText("Rang");
            t.getRow(0).getCell(1).setText("Candidat");
            t.getRow(1).getCell(0).setText("{{POUR:TITULAIRES}}{{TIT.RANG}}");
            t.getRow(1).getCell(1).setText("{{TIT.CANDIDAT}}");
        });
        byte[] rempli = ModelePvWord.remplir(modele, ctx("FAV"), CONDITIONS);
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(rempli))) {
            XWPFTable t = doc.getTables().get(0);
            assertThat(t.getRows()).hasSize(3);
            assertThat(t.getRow(1).getCell(0).getText()).isEqualTo("1");
            assertThat(t.getRow(1).getCell(1).getText()).isEqualTo("BTP Alpha");
            assertThat(t.getRow(2).getCell(1).getText()).isEqualTo("BTP Gamma");
            XWPFRun r = doc.getParagraphs().get(0).getRuns().get(0);
            assertThat(r.getCTR().sizeOfBrArray()).isEqualTo(1);
            assertThat(doc.getParagraphs().get(0).getText()).isEqualTo("Objet : FOURNITURE\nDE PRODUITS");
        }
    }

    @Test
    void lesAnciensMarqueursDuPlanSontLusCommeLesVariables() throws Exception {
        byte[] modele = docx(doc -> paragraphe(doc, "Fait à <CHEF", "-LIEU > le <DATE EXAMEN>"));
        assertThat(ModelePvWord.texte(ModelePvWord.remplir(modele, ctx("FAV"), CONDITIONS))).isEqualTo("Fait à Antananarivo le 22 octobre 2026");
        assertThat(ModelePvWord.relever(modele).marqueurs()).extracting(ModelePvWord.Marqueur::type, ModelePvWord.Marqueur::code)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("ALIAS", "COMMISSION.CHEF_LIEU"),
                        org.assertj.core.groups.Tuple.tuple("ALIAS", "PV.DATE_EXAMEN"));
    }

    @Test
    void leReleveSignaleLesBlocsMalFermesEtLesMarqueursCoupesParUneTabulation() throws Exception {
        byte[] modele = docx(doc -> {
            paragraphe(doc, "{{SI:A}} texte");
            XWPFParagraph p = doc.createParagraph();
            p.createRun().setText("{{PV.");
            XWPFRun tab = p.createRun();
            tab.addTab();
            tab.setText("REFERENCE}}");
            XWPFTable t = doc.createTable(1, 2);
            t.getRow(0).getCell(0).setText("{{FINSI:A}}");
            t.getRow(0).getCell(1).setText("{{POUR:TITULAIRES}}");
        });
        ModelePvWord.Releve r = ModelePvWord.relever(modele);
        assertThat(r.defauts()).extracting(ModelePvWord.Defaut::code)
                .containsExactlyInAnyOrder("MARQUEUR_COUPE", "BLOC_MAL_FERME", "BLOC_NON_FERME");
        assertThat(r.defauts()).filteredOn(d -> d.code().equals("MARQUEUR_COUPE")).extracting(ModelePvWord.Defaut::ou).containsExactly("paragraphe 2");
        ModelePvWord.Marqueur pour = r.marqueurs().stream().filter(m -> "POUR".equals(m.type())).findFirst().orElseThrow();
        assertThat(pour.premiereCellule()).isFalse();
        assertThat(pour.ou()).isEqualTo("tableau 1, ligne 1, cellule 2, paragraphe 1");
    }
}
