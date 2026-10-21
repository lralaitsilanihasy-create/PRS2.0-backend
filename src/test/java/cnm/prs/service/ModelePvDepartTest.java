package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;

import cnm.prs.dto.ModelePvDto;

/**
 * ⚠️ 2026-10-21 (demande front « modele-pv-depart ») — le modèle de départ assemblé depuis le PV officiel du plan : aucun ancien
 * chevron, blocs équilibrés, et rempli sur le dossier fictif du catalogue, le bon avis, l'annexe seulement avec réserves, la bonne
 * commission selon la portée, les titulaires pour le dossier de marché.
 */
class ModelePvDepartTest {

    private static Map<String, String> conditions(ModelePvDepart.Depart d) {
        Map<String, String> out = new LinkedHashMap<>();
        d.conditions().forEach(c -> out.put(c.nom(), c.expression()));
        return out;
    }

    private static String rempli(ModelePvDepart.Depart d, String avis, String portee, Map<String, String> saisis) throws IOException {
        ModelePvWord.Contexte ex = CatalogueModelesPv.exemple();
        Map<String, String> cles = new HashMap<>(ex.cles());
        cles.put("avis", avis);
        cles.put("portee", portee);
        byte[] docx = ModelePvWord.remplir(d.docx(), new ModelePvWord.Contexte(ex.variables(), ex.listes(), cles, saisis), conditions(d));
        return ModelePvWord.texte(docx);
    }

    @Test
    void assembleSansChevronNiDefaut() throws Exception {
        for (ModelePvDepart.Famille f : ModelePvDepart.FAMILLES.values()) {
            for (String portee : ModelePvDepart.PORTEES) {
                ModelePvDepart.Depart d = ModelePvDepart.assembler(f, portee);
                ModelePvWord.Releve rel = ModelePvWord.relever(d.docx());
                assertThat(rel.defauts()).as(f.code() + " " + portee).isEmpty();
                assertThat(rel.marqueurs()).as(f.code() + " " + portee).noneMatch(m -> "ALIAS".equals(m.type()));
                assertThat(ModelePvWord.texte(d.docx())).doesNotContain("<", "PPM", "Plan de Passation");
                assertThat(d.conditions()).extracting(ModelePvDto.Condition::nom).hasSize(ModelePvDepart.LES_DEUX.equals(portee) ? 6 : 4);
                assertThat(d.champs()).extracting(ModelePvDto.Champ::code).containsExactly("motif_defavorable", "observation_generale");
            }
        }
        assertThat(ModelePvDepart.assembler(ModelePvDepart.FAMILLES.get("DMC"), "les-deux").bases())
                .containsExactly("PV_AFSR_PPM_CENTRALE.docx", "PV_AFSR_PPM_REGIONALE.docx");
    }

    @Test
    void avisAnnexeEtPortee() throws Exception {
        ModelePvDepart.Depart d = ModelePvDepart.assembler(ModelePvDepart.FAMILLES.get("DMC"), "les-deux");
        String fav = rempli(d, "FAV", "centrale", Map.of());
        assertThat(fav).contains("PROCÈS-VERBAL D’EXAMEN DU DOSSIER DE MISE EN CONCURRENCE", "COMMISSION CENTRALE DES MARCHES",
                "La Commission Centrale des Marchés émet un AVIS FAVORABLE au dossier de mise en concurrence.",
                "L’an deux mille vingt-six", "Commission Centrale des Marchés, instituée",
                "Fourniture de produits d'entretien et des petits matériels (procédure 13-CC/MEF/SG/PRMP.2026, Appel d'offres ouvert, lot 1)")
                .doesNotContain("ANNEXE", "{{", "REGIONALE", "Régionale", "Observations générales", "sous réserve", "NON FAVORABLE");
        String favr = rempli(d, "FAVR", "regionale", Map.of("observation_generale", "Dossier complet."));
        assertThat(favr).contains("COMMISSION REGIONALE DES MARCHES", "\nAntananarivo\n", "La Commission Régionale des Marchés émet un AVIS "
                + "FAVORABLE au dossier de mise en concurrence, sous réserve qu’il soit tenu compte des observations portées en annexe.",
                "Observations générales de la Commission : Dossier complet.", "ANNEXE", "[Dossier] Pièces administratives")
                .doesNotContain("CENTRALE", "{{");
        String def = rempli(d, "DEF", "centrale", Map.of("motif_defavorable", "pièces manquantes"));
        assertThat(def).contains("AVIS NON FAVORABLE au dossier de mise en concurrence, pour le motif suivant : pièces manquantes.")
                .doesNotContain("ANNEXE");

        // Le gras de « AVIS FAVORABLE » est celui de la base.
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(d.docx()))) {
            XWPFParagraph avis = doc.getParagraphs().stream().filter(p -> p.getText().contains("émet un")).findFirst().orElseThrow();
            assertThat(avis.getRuns().stream().filter(r -> r.text().contains("AVIS")).allMatch(r -> r.isBold())).isTrue();
            assertThat(avis.getRuns().get(0).isBold()).isFalse();
        }
    }

    @Test
    void marcheAvecSesTitulaires() throws Exception {
        ModelePvDepart.Depart d = ModelePvDepart.assembler(ModelePvDepart.FAMILLES.get("DDM"), "centrale");
        assertThat(ModelePvWord.relever(d.docx()).marqueurs()).anyMatch(m -> "POUR".equals(m.type()) && "TITULAIRES".equals(m.code())
                && m.premiereCellule());
        String texte = rempli(d, "FAV", "centrale", Map.of());
        assertThat(texte).contains("PROCÈS-VERBAL D’EXAMEN DU DOSSIER DE MARCHÉ", "AVIS FAVORABLE à l’attribution du marché.",
                "Titulaire(s) proposé(s) :", "Hygiène & Services Madagascar SARL", "Entreprise de démonstration SARL",
                "Écart à l’estimation : −22,5 %", "issu du dossier de mise en concurrence 00003/DAOO-CC/CNM/2026, avis favorable")
                .doesNotContain("{{");
        assertThat(texte.indexOf("Titulaire(s) proposé(s)")).isGreaterThan(texte.indexOf("émet un"))
                .isLessThan(texte.indexOf("VISA DU SUPERIEUR"));
        assertThat(rempli(ModelePvDepart.assembler(ModelePvDepart.FAMILLES.get("DGC"), "centrale"), "FAV", "centrale", Map.of()))
                .contains("PROCÈS-VERBAL D’EXAMEN DE L’ACTE DE GESTION CONTRACTUELLE", "AVIS FAVORABLE à l’acte de gestion.")
                .doesNotContain("Titulaire");
    }
}
