package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.Dossier;
import cnm.prs.entity.Marche;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.TypeDmc;
import cnm.prs.enums.FormeMarche;
import cnm.prs.service.ChampFicheMarcheService;

/**
 * ⚠️ <strong>Marché à commande (lot 3) et contrat-cadre (lot 4)</strong> — demandes front du 2026-09-23. Le référentiel
 * est celui des fichiers de correspondance du front, chargés par l'import ({@code src/test/resources/fiche-marche},
 * copies de {@code frontendprs2/docs}) : 116 champs des fournitures, 118 du contrat-cadre.
 *
 * <p>Jeu : plan 9900 (PRMP001, ANT, CLOTURE, PV signé FAV), lignes en appel d'offres ouvert : 9901 à quantité fixe, 9902
 * à commande, 9903 contrat-cadre.</p>
 */
class FicheMarcheCommandeEtContratCadreIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired private ChampFicheMarcheService champService;

    @BeforeEach
    void jeu() throws Exception {
        TypeDmc dao = typeDmcRepository.findByCode("DAO").orElseThrow();
        ModePassation m92 = new ModePassation(92, "Appel d'offres ouvert", null, null, null, null);
        m92.setIdTypeDmc(dao.getIdTypeDmc());
        modePassationRepository.save(m92);
        dossierRepository.save(dossierLoc(9900, "CLOTURE", "ANT", "PRMP001"));
        Dossier plan = dossierRepository.findById(9900).orElseThrow();
        plan.setIdEntiteContract(1);
        dossierRepository.save(plan);
        ppmRepository.save(ppm(9900, 9900, "PRMP001"));
        receptionRepository.save(reception(9900, 9900, "CTRCC1", true));
        dispatchRepository.save(dispatch(9900, 9900, "CTRCC1", "CTRMEM", "CTRPRE"));
        examenRepository.save(examen(9900, 9900, "CTRMEM"));
        seedPvSigne(9900, 9900);
        ligne(9901, FormeMarche.QUANTITE_FIXE);
        ligne(9902, FormeMarche.A_COMMANDE);
        ligne(9903, FormeMarche.CONTRAT_CADRE);
    }

    // ------------------------------------------------------------------ 1. chargement des référentiels

    @Test
    @DisplayName("1 — Import : 116 champs des fournitures et 118 du contrat-cadre, aucun rejet ; champs actifs servis : 139 en "
            + "quantité fixe, 146 à commande, 152 en contrat-cadre (35 repris et reflets + 117)")
    void chargementDesReferentiels() throws Exception {
        ChampFicheMarcheService.BilanImport f = importer("referentiel-champs-fiche-marche-fournitures.csv");
        assertThat(f.rejets()).isEmpty();
        assertThat(f.crees()).hasSize(116);
        ChampFicheMarcheService.BilanImport cc = importer("referentiel-champs-fiche-marche-contrat-cadre.csv");
        assertThat(cc.rejets()).isEmpty();
        assertThat(cc.crees()).hasSize(118);

        assertThat(champs("QUANTITE_FIXE")).hasSize(139);
        assertThat(champs("A_COMMANDE")).hasSize(146);
        assertThat(champs("CONTRAT_CADRE")).hasSize(152);
    }

    // ------------------------------------------------------------------ 2. rubriques servies par type (B4)

    @Test
    @DisplayName("2 — Contrat-cadre : B07 et ses 8 rubriques, aucune rubrique partagée des fournitures ; quantité fixe et à "
            + "commande : aucune des 38 rubriques du contrat-cadre, pas de B07")
    void rubriquesParType() throws Exception {
        importer("referentiel-champs-fiche-marche-fournitures.csv");
        importer("referentiel-champs-fiche-marche-contrat-cadre.csv");
        String cc = referentiel("CONTRAT_CADRE");
        assertThat(JsonPath.<List<String>>read(cc, "$.blocs[*].code")).contains("B07");
        assertThat(JsonPath.<List<String>>read(cc, "$.blocs[?(@.code=='B07')].rubriques[*].code"))
                .containsExactly("B07-PS", "B07-FS", "B07-MA", "B07-TN", "B07-PI", "B07-DU", "B07-DE", "B07-PE");
        List<String> rubriquesCc = JsonPath.read(cc, "$.blocs[*].rubriques[*].code");
        assertThat(rubriquesCc).contains("B04-RQ", "B09-AU", "B01-AC").doesNotContain("B02-AU", "B04-RO", "B09-AS");

        for (String type : List.of("QUANTITE_FIXE", "A_COMMANDE")) {
            String ref = referentiel(type);
            assertThat(JsonPath.<List<String>>read(ref, "$.blocs[*].code")).as(type).doesNotContain("B07");
            List<String> rubriques = JsonPath.read(ref, "$.blocs[*].rubriques[*].code");
            assertThat(rubriques).as(type).contains("B02-AU", "B04-RO").doesNotContain("B04-RQ", "B09-AU", "B02-OE");
        }
    }

    // ------------------------------------------------------------------ 3. marché à commande (lot 3)

    @Test
    @DisplayName("3 — À commande : éligible et outillé, DMC créé, cadrage des neuf questions sans attributaires, typeOutille ; "
            + "validation → DPAO, CCAP et AE, le DPAO porte les quantités minimum et maximum, l'AE les montants annuels")
    void marcheACommande() throws Exception {
        importer("referentiel-champs-fiche-marche-fournitures.csv");
        String eligibles = mvc.perform(get("/api/dmcs/eligibles").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Boolean>>read(eligibles, "$[?(@.idDetail==9902)].formeOutillee")).containsExactly(true);
        Long idDmc = creerDmc(9902);
        cadrage(idDmc, "{\"alloti\":\"NON\",\"variantes\":\"NON\",\"groupement\":\"NON\",\"provenance\":\"NATIONAL\","
                + "\"typePrix\":\"UNITAIRES\",\"prixRevisable\":\"NON\",\"garantieSoumission\":\"NON\",\"avance\":\"NON\","
                + "\"penalites\":\"CCAG\"}");
        mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.typeMarche").value("A_COMMANDE"))
                .andExpect(jsonPath("$.typeOutille").value(true));

        Map<String, String> propres = new LinkedHashMap<>();
        propres.put("B02-AU-03", "100 à 500 unités");
        propres.put("B05-TP-02", "10000000");
        propres.put("B05-TP-03", "50000000");
        remplirEtValider(idDmc, "A_COMMANDE", propres);

        List<String> types = JsonPath.read(documents(idDmc), "$[*].type");
        assertThat(types).containsExactly("DPAO", "DPAO", "CCAP", "CCAP", "AE", "AE");
        assertThat(texte(idDmc, "DPAO")).contains("Quantités minimum et maximum : 100 à 500 unités");
        assertThat(texte(idDmc, "AE")).contains("Montant minimum annuel", "10 000 000 Ariary", "Montant maximum annuel");
    }

    // ------------------------------------------------------------------ 4. contrat-cadre (lot 4)

    @Test
    @DisplayName("4 — Contrat-cadre : outillé, 23 informations reprises du plan, attributaires MONO ferme la remise en "
            + "concurrence et MULTI l'ouvre ; validation → DPAC et AE seulement, jamais DPAO ni CCAP")
    void contratCadre() throws Exception {
        importer("referentiel-champs-fiche-marche-contrat-cadre.csv");
        Long idDmc = creerDmc(9903);
        String fiche = mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.typeMarche").value("CONTRAT_CADRE"))
                .andExpect(jsonPath("$.typeOutille").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Map<String, Object>>read(fiche, "$.valeursPpm")).hasSize(23);

        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"attributaires\":2}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erreurs[0].champ").value("attributaires"));
        cadrage(idDmc, "{\"alloti\":\"NON\",\"groupement\":\"NON\",\"avance\":\"NON\",\"typePrix\":\"UNITAIRES\","
                + "\"attributaires\":\"MONO\"}");
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B07").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":{\"B07-MA-01\":10,\"B07-MA-03\":\"Après remise en concurrence\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valeurs.B07-MA-01").value("10"))
                .andExpect(jsonPath("$.valeurs.B07-MA-03").doesNotExist());
        cadrage(idDmc, "{\"alloti\":\"NON\",\"groupement\":\"NON\",\"avance\":\"NON\",\"typePrix\":\"UNITAIRES\","
                + "\"attributaires\":\"MULTI\"}");
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B07").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":{\"B07-MA-01\":10,\"B07-MA-03\":\"Après remise en concurrence\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valeurs.B07-MA-03").value("Après remise en concurrence"))
                .andExpect(jsonPath("$.valeurs.B07-MA-01").doesNotExist());

        remplirEtValider(idDmc, "CONTRAT_CADRE", Map.of("B05-MT-01", "250000000"));
        List<String> types = JsonPath.read(documents(idDmc), "$[*].type");
        assertThat(types).containsExactly("DPAC", "DPAC", "AE", "AE");
        assertThat(texte(idDmc, "DPAC")).contains("Données particulières du cahier des clauses administratives",
                "Calendrier prévisionnel");
        assertThat(texte(idDmc, "AE")).contains("Marchés subséquents", "Attribution des marchés subséquents",
                "Montant indicatif du contrat-cadre hors taxes (Ariary) : 250 000 000 Ariary");
    }

    // ------------------------------------------------------------------ 5. quantité fixe inchangée

    @Test
    @DisplayName("5 — Quantité fixe : typeOutille vrai, B07 absent des blocs, et rien du contrat-cadre dans ses champs")
    void quantiteFixeInchangee() throws Exception {
        importer("referentiel-champs-fiche-marche-contrat-cadre.csv");
        Long idDmc = creerDmc(9901);
        mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.typeOutille").value(true))
                .andExpect(jsonPath("$.bilanControles.nbAttendus").value(0));
        assertThat(champs("QUANTITE_FIXE")).noneMatch(c -> c.startsWith("B07-") || c.startsWith("B02-OE"));
    }

    // ------------------------------------------------------------------ outils

    private ChampFicheMarcheService.BilanImport importer(String fichier) throws Exception {
        Path chemin = new ClassPathResource("fiche-marche/" + fichier).getFile().toPath();
        return champService.importerCsv(chemin);
    }

    private String referentiel(String type) throws Exception {
        return mvc.perform(get("/api/champs-fiche-marche").param("typeMarche", type).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private List<String> champs(String type) throws Exception {
        return JsonPath.read(referentiel(type), "$.champs[*].code");
    }

    /**
     * Renseigne les valeurs données, puis tout champ obligatoire que le bilan déclare manquant (valeur typée, dates
     * croissantes dans l'ordre des codes), jusqu'à ce que la fiche se valide.
     */
    private void remplirEtValider(Long idDmc, String type, Map<String, String> donnees) throws Exception {
        String ref = referentiel(type);
        Map<String, String> types = new TreeMap<>();
        Map<String, List<String>> options = new TreeMap<>();
        Map<String, String> controles = new TreeMap<>();
        List<Map<String, Object>> champs = JsonPath.read(ref, "$.champs[?(@.source=='SAISIE')]");
        for (Map<String, Object> c : champs) {
            types.put((String) c.get("code"), (String) c.get("type"));
            @SuppressWarnings("unchecked")
            List<String> o = (List<String>) c.get("options");
            options.put((String) c.get("code"), o);
            controles.put((String) c.get("code"), (String) c.get("controle"));
        }
        Map<String, Map<String, Object>> parBloc = new TreeMap<>();
        donnees.forEach((code, v) -> parBloc.computeIfAbsent(code.substring(0, 3), k -> new LinkedHashMap<>()).put(code, v));
        for (int tour = 0; tour < 3; tour++) {
            for (Map.Entry<String, Map<String, Object>> e : parBloc.entrySet()) {
                mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/" + e.getKey()).header("Authorization", tokenPrmp)
                        .contentType(JSON).content("{\"valeurs\":" + new tools.jackson.databind.ObjectMapper()
                                .writeValueAsString(e.getValue()) + "}"))
                        .andExpect(status().isOk());
            }
            String fiche = mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<String> manquants = JsonPath.read(fiche, "$.bilanControles.bloquants[?(@.regle=='OBLIGATOIRE')].champs[0]");
            if (manquants.isEmpty()) {
                break;
            }
            for (String code : manquants) {
                parBloc.computeIfAbsent(code.substring(0, 3), k -> new LinkedHashMap<>())
                        .put(code, valeurPour(types.get(code), options.get(code), controles.get(code)));
            }
        }
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/valider").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
    }

    private static Object valeurPour(String type, List<String> options, String controle) {
        return switch (type == null ? "TEXTE" : type) {
            case "DATE" -> LocalDate.of(2026, 1, 1).plusDays(10L * rangCalendrier(controle)).toString();
            case "NOMBRE" -> 30;
            case "MONTANT" -> 1000000;
            case "POURCENTAGE" -> 10;
            case "OUI_NON" -> "NON";
            case "LISTE" -> options == null || options.isEmpty() ? "x" : options.get(0);
            default -> "Clause type";
        };
    }

    /** Rang de l'étape dans le calendrier (DATES_ORDRE:ROLE) : les dates saisies sont croissantes, les autres au début. */
    private static int rangCalendrier(String controle) {
        List<String> etapes = List.of("LANCEMENT", "REMISE", "OUVERTURE", "ATTRIBUTION", "NOTIFICATION");
        return controle == null || !controle.startsWith("DATES_ORDRE:") ? 0
                : etapes.indexOf(controle.substring("DATES_ORDRE:".length())) + 1;
    }

    private String documents(Long idDmc) throws Exception {
        return mvc.perform(get("/api/fiches-marche/" + idDmc + "/documents").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String texte(Long idDmc, String type) throws Exception {
        int id = JsonPath.<List<Integer>>read(documents(idDmc),
                "$[?(@.type=='" + type + "' && @.extension=='docx')].idDocument").get(0);
        byte[] docx = mvc.perform(get("/api/fiches-marche/documents/" + id + "/contenu").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx));
                XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
            return ex.getText();
        }
    }

    private Long creerDmc(int idDetail) throws Exception {
        String corps = mvc.perform(post("/api/dmcs/par-marche/" + idDetail).header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(corps, "$.idDmc")).longValue();
    }

    private void cadrage(Long idDmc, String cadrage) throws Exception {
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":" + cadrage + "}")).andExpect(status().isOk());
    }

    private void ligne(int idDetail, FormeMarche forme) {
        Marche l = marche(idDetail, 9900, 9900);
        l.setIdMode(92);
        l.setFormeMarche(forme);
        l.setDesignationMarche("Marché " + idDetail);
        marcheRepository.save(l);
    }
}
