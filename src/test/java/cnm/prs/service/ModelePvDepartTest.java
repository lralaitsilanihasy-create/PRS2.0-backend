package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

/**
 * ⚠️ 2026-10-21 (« modele-pv-depart », puis « modele-pv-page » §B5) — le modèle de départ est une page reconstruite : valide pour
 * toutes les familles et portées, sans ancien chevron, avec ses blocs d'avis, son annexe (saut de page compris) et, pour le dossier de
 * marché, ses titulaires ; rendu en une page sans réserves, deux avec l'annexe.
 */
class ModelePvDepartTest {

    private static String textes(JsonNode n) {
        StringBuilder s = new StringBuilder();
        if (n.path("text").isString()) {
            s.append(n.path("text").asString());
        }
        for (JsonNode e : n.isObject() ? n.values() : n) {
            if (e.isObject() || e.isArray()) {
                s.append(textes(e));
            }
        }
        return s.toString();
    }

    @Test
    void valideSansChevronPourChaqueFamilleEtPortee() {
        for (ModelePvDepart.Famille f : ModelePvDepart.FAMILLES.values()) {
            for (String portee : ModelePvDepart.PORTEES) {
                ModelePvDepart.Depart d = ModelePvDepart.assembler(f, portee, 3);
                ModelePvPage.valider(d.document(), id -> id == 3);
                assertThat(textes(d.document())).doesNotContain("<", "{{").contains("PROCÈS-VERBAL D’EXAMEN " + f.titre());
                List<String> codes = ModelePvPage.relever(d.document()).marqueurs().stream().map(m -> m.type() + ":" + m.code()).toList();
                assertThat(codes).contains("VARIABLE:PV.REFERENCE", "VARIABLE:PV.MEMBRE", "SI:FAVORABLE", "SI:AVEC_RESERVES",
                        "SI:DEFAVORABLE", "SAISI:motif_defavorable", "SAISI:observation_generale", "POUR:OBSERVATIONS");
                assertThat(codes.contains("POUR:TITULAIRES")).isEqualTo(f.titulaires());
                assertThat(codes.contains("SI:CENTRALE")).isEqualTo(ModelePvDepart.LES_DEUX.equals(portee));
                assertThat(d.conditions()).hasSize(ModelePvDepart.LES_DEUX.equals(portee) ? 6 : 4);
            }
        }
    }

    @Test
    void uneOuDeuxPagesSelonLAvis() throws Exception {
        ModelePvDepart.Depart d = ModelePvDepart.assembler(ModelePvDepart.FAMILLES.get("DDM"), ModelePvDepart.LES_DEUX, 3);
        Map<String, String> cond = new LinkedHashMap<>();
        d.conditions().forEach(c -> cond.put(c.nom(), c.expression()));
        ModelePvWord.Contexte ex = CatalogueModelesPv.exemple();
        for (String avis : List.of("FAV", "FAVR", "DEF")) {
            Map<String, String> cles = new java.util.HashMap<>(ex.cles());
            cles.put("avis", avis);
            cles.put("portee", "regionale");
            ModelePvWord.Contexte ctx = new ModelePvWord.Contexte(ex.variables(), ex.listes(), cles, Map.of("motif_defavorable", "pièces manquantes"));
            byte[] pdf = ModelePvPage.pdf(ModelePvPage.xhtml(d.document(), ctx, cond, id -> null));
            try (PDDocument doc = Loader.loadPDF(pdf)) {
                String t = trie().getText(doc).replaceAll("\\s+", " ");
                assertThat(doc.getNumberOfPages()).as(avis).isEqualTo("FAVR".equals(avis) ? 2 : 1);
                assertThat(t).contains("Commission Régionale des Marchés", ex.variables().get("COMMISSION.LOCALITE").texte(),
                        "Titulaire(s) proposé(s)", "Hygiène & Services Madagascar SARL").doesNotContain("Centrale", "{{");
                assertThat(t.contains("ANNEXE")).isEqualTo("FAVR".equals(avis));
                if ("DEF".equals(avis)) {
                    assertThat(t).contains("AVIS NON FAVORABLE à l’attribution du marché, pour le motif suivant : pièces manquantes.");
                }
            }
        }
    }

    /**
     * ⚠️ 2026-10-21 (« modele-lettre-renvoi », B2) — la lettre de départ : valide pour chaque portée, sans marqueur resté en texte,
     * avec ses variables requises ; une page, la ligne de commission et le titre du signataire selon la portée, le corps ligne à ligne.
     */
    @Test
    void lettreDeRenvoi() throws Exception {
        for (String portee : ModelePvDepart.PORTEES) {
            ModelePvDepart.Depart d = ModelePvDepart.lettre(portee, 3);
            ModelePvPage.valider(d.document(), id -> id == 3);
            assertThat(textes(d.document())).doesNotContain("<", "{{").contains("lettre de renvoi");
            List<String> codes = ModelePvPage.relever(d.document()).marqueurs().stream().map(m -> m.type() + ":" + m.code()).toList();
            assertThat(codes).contains("VARIABLE:DOSSIER.REFERENCE", "VARIABLE:LETTRE.CORPS", "VARIABLE:LETTRE.SIGNATAIRE",
                    "VARIABLE:LETTRE.DATE", "VARIABLE:LETTRE.DATE_EXAMEN", "VARIABLE:DOSSIER.ENTITE")
                    .noneMatch(c -> c.startsWith("VARIABLE:PV."));
            assertThat(codes.contains("SI:CENTRALE")).isEqualTo(ModelePvDepart.LES_DEUX.equals(portee));
            assertThat(codes.contains("VARIABLE:LETTRE.LOCALITE")).isEqualTo(!ModelePvDepart.CENTRALE.equals(portee));
            assertThat(d.conditions()).hasSize(ModelePvDepart.LES_DEUX.equals(portee) ? 2 : 0);
            assertThat(d.champs()).isEmpty();
        }
        ModelePvDepart.Depart d = ModelePvDepart.lettre(ModelePvDepart.LES_DEUX, 3);
        Map<String, String> cond = new LinkedHashMap<>();
        d.conditions().forEach(c -> cond.put(c.nom(), c.expression()));
        ModelePvWord.Contexte ex = CatalogueModelesPv.exemple();
        for (String portee : List.of("centrale", "regionale")) {
            Map<String, String> cles = new java.util.HashMap<>(ex.cles());
            cles.put("portee", portee);
            byte[] pdf = ModelePvPage.pdf(ModelePvPage.xhtml(d.document(), new ModelePvWord.Contexte(ex.variables(), ex.listes(), cles,
                    Map.of()), cond, id -> null));
            try (PDDocument doc = Loader.loadPDF(pdf)) {
                String t = trie().getText(doc).replace("\r\n", "\n");
                assertThat(doc.getNumberOfPages()).as(portee).isEqualTo(1);
                assertThat(t).contains("Antananarivo, le 23 octobre 2026", "en date du 22 octobre 2026",
                        "- produire l'attestation fiscale à jour ;\n- corriger le délai de validité des offres.", "Andry RAKOTOMALALA");
                if ("centrale".equals(portee)) {
                    assertThat(t).contains("COMMISSION CENTRALE", "Le Président de la Commission Nationale des Marchés")
                            .doesNotContain("REGIONALE", "TOAMASINA");
                } else {
                    assertThat(t).contains("COMMISSION REGIONALE DES MARCHES TOAMASINA", "Le Chef de la Commission Régionale des Marchés")
                            .doesNotContain("COMMISSION CENTRALE", "Le Président");
                }
            }
        }
    }

    /** Le texte dans l’ordre de la page (un texte justifié ou un compteur sinon sortent dans le désordre). */
    static PDFTextStripper trie() throws java.io.IOException {
        PDFTextStripper s = new PDFTextStripper();
        s.setSortByPosition(true);
        return s;
    }
}
