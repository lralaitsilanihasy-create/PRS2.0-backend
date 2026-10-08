package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

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
import cnm.prs.entity.Nature;
import cnm.prs.entity.TypeDmc;
import cnm.prs.enums.FormeMarche;
import cnm.prs.service.ChampFicheMarcheService;

/**
 * ⚠️ <strong>Fiche DAO des prestations intellectuelles</strong> (demande front du 2026-09-24, troisième référentiel) —
 * le DPIC admis comme document maître et produit (V42), les 44 rubriques, l'import des 90 champs, la catégorie ouverte :
 * une fiche de prestations intellectuelles produit DPIC, CCAP et AE, jamais de DPAO.
 *
 * <p>Jeu : plan 9900 (PRMP001, ANT, CLOTURE, PV signé FAV), ligne 9901 en appel d'offres ouvert à quantité fixe, nature
 * 94 « Prestations intellectuelles ».</p>
 */
class FicheDaoPrestationsIntellectuellesIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired private ChampFicheMarcheService champService;

    private ChampFicheMarcheService.BilanImport bilan;

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
        natureRepository.save(new Nature(94, "Prestations intellectuelles", null, "PRESTATIONS_INTELLECTUELLES"));
        Marche l = marche(9901, 9900, 9900);
        l.setIdMode(92);
        l.setFormeMarche(FormeMarche.QUANTITE_FIXE);
        l.setIdNature(94);
        l.setDesignationMarche("Étude de faisabilité du schéma directeur");
        marcheRepository.save(l);

        bilan = champService.importerCsv(new ClassPathResource(
                "fiche-marche/referentiel-champs-fiche-dao-prestations-intellectuelles.csv").getFile().toPath());
    }

    @Test
    @DisplayName("1 — Import : 114 champs (107 avant le lot D3, 90 avant V50), aucun rejet (DPIC admis) ; référentiel des prestations "
            + "intellectuelles : 23 informations du plan + 111 actives, ses rubriques seulement, pas de B07 ni de B11 — lot D3 (29/09) : "
            + "sept champs créés, B02-MS-01 à quatre options, B04-LH-02 date-heure, B08-IP-01 en points, B09-DP-01 oui/non, "
            + "B09-PP-01 retiré, B09-FC-01 réactivé, la question des pénalités (B09-PR-01, V53) posée")
    void chargement() throws Exception {
        assertThat(bilan.rejets()).isEmpty();
        assertThat(bilan.crees()).hasSize(114);   // V50 : + 17 champs B04-SE (trois catégories) ; lot D3 : + 7
        String ref = mvc.perform(get("/api/champs-fiche-marche?typeMarche=QUANTITE_FIXE&categorie=PRESTATIONS_INTELLECTUELLES")
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        // V50 : 88 + 17 B04-SE − B04-VE-01/02 inactifs = 103 ; lot D3 : + 7 créés − B09-PP-01 + B09-FC-01 + B09-PR-01 = 111
        // ⚠️ 2026-09-30 (arbitrage du pilote, §B2.3) — − 13 champs qu'aucun document n'utilise = 98
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[*].code")).hasSize(23 + 98)
                .doesNotContain("B03-TP-01", "B06-TP-01", "B04-QT-01").contains("B03-SP-03", "B05-PF-02");
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[?(@.documentMaitre=='DPIC')].code")).isNotEmpty();
        assertThat(JsonPath.<List<String>>read(ref, "$.blocs[*].code")).doesNotContain("B07", "B11");
        assertThat(JsonPath.<List<String>>read(ref, "$.blocs[*].rubriques[*].code")).contains("B02-CL", "B02-MS", "B01-AC", "B09-FC")
                .doesNotContain("B02-AU", "B02-LT");

        // ⚠️ Lot D3 (2026-09-29, §B2)
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[*].code"))
                .contains("B04-EP-04", "B05-PF-13", "B06-TP-07", "B06-CS-02", "B06-CS-03", "B08-AI-03", "B09-OP-02", "B09-FC-01", "B09-PR-01")
                .doesNotContain("B09-PP-01");
        assertThat(JsonPath.<List<List<String>>>read(ref, "$.champs[?(@.code=='B02-MS-01')].options").get(0)).containsExactly(
                "Qualité technique, expérience et proposition financière",
                "Budget prédéterminé dont le candidat propose la meilleure utilisation",
                "Meilleure proposition financière parmi les candidats ayant obtenu la note technique minimale",
                "Qualité technique exclusivement", "Qualification du consultant");   // ⚠️ 2026-10-08 (lot 3 PI, Q6)
        assertThat(JsonPath.<List<List<String>>>read(ref, "$.champs[?(@.code=='B04-LP-01')].options").get(0))
                .containsExactly("Français", "Français et une seconde langue", "Une autre langue que le français");
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[?(@.code=='B04-LH-02')].type")).containsExactly("DATE_HEURE");
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[?(@.code=='B08-IP-01')].type")).containsExactly("NOMBRE");
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[?(@.code=='B09-DP-01')].type")).containsExactly("OUI_NON");
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[?(@.code=='B08-AI-03')].controle")).containsExactly("AVANCE_MAX_20:TAUX");
    }

    @Test
    @DisplayName("2 — Fiche de prestations intellectuelles : outillée, catégorie servie ; validation → DPIC, CCAP et AE, "
            + "jamais de DPAO ; le DPIC porte son intitulé")
    void validationProduitLeDpic() throws Exception {
        String dmc = mvc.perform(post("/api/dmcs/par-marche/9901").header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long idDmc = ((Number) JsonPath.read(dmc, "$.idDmc")).longValue();
        mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categorie").value("PRESTATIONS_INTELLECTUELLES"))
                .andExpect(jsonPath("$.typeOutille").value(true));
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"groupement\":\"NON\",\"avance\":\"NON\",\"prixRevisable\":\"NON\"}}"))
                .andExpect(status().isOk());
        remplirObligatoiresEtValider(idDmc, "QUANTITE_FIXE", "PRESTATIONS_INTELLECTUELLES", Map.of());

        String documents = mvc.perform(get("/api/fiches-marche/" + idDmc + "/documents").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(documents, "$[*].type")).containsExactly("DPIC", "DPIC", "CCAP", "CCAP", "AE", "AE");
        assertThat(JsonPath.<List<String>>read(documents, "$[?(@.type=='DPIC')].libelle"))
                .containsOnly("Données particulières des instructions aux consultants");
        assertThat(JsonPath.<List<String>>read(documents, "$[*].nomFichier")).contains("DPIC_DOS-9900_9901_v1.docx");
        // ⚠️ Lot D3 (2026-09-29, §B3) — les trois documents sont rendus des documents types : le CPS tient le rôle du CCAP.
        assertThat(JsonPath.<List<String>>read(documents, "$[?(@.type=='CCAP')].libelle")).containsOnly("Cahier des prescriptions spéciales");
        assertThat(texte(documents, "DPIC")).contains("1.3. DONNEES PARTICULIERES DES INSTRUCTIONS AUX CANDIDATS").doesNotContain("{{");
        assertThat(texte(documents, "AE")).contains("ACTE D'ENGAGEMENT (A.E)").doesNotContain("{{");
        assertThat(texte(documents, "CCAP")).contains("MARCHÉ PUBLIC DE PRESTATIONS INTELLECTUELLES");   // prix fermes : pas d'annexe de révision
    }

    private String texte(String documents, String type) throws Exception {
        int id = JsonPath.<List<Integer>>read(documents, "$[?(@.type=='" + type + "' && @.extension=='docx')].idDocument").get(0);
        byte[] docx = mvc.perform(get("/api/fiches-marche/documents/" + id + "/contenu").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(
                new java.io.ByteArrayInputStream(docx));
                org.apache.poi.xwpf.extractor.XWPFWordExtractor ex = new org.apache.poi.xwpf.extractor.XWPFWordExtractor(doc)) {
            return ex.getText();
        }
    }
}
