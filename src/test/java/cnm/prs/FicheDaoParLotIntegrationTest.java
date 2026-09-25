package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.dto.ChampFicheMarcheDto;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Lot;
import cnm.prs.entity.Marche;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.TypeDmc;
import cnm.prs.enums.FormeMarche;
import cnm.prs.exception.ChampsInvalidesException;
import cnm.prs.service.ChampFicheMarcheService;

/**
 * ⚠️ <strong>La fiche DAO à commande au niveau d'un dossier réel</strong> (demande front du 2026-09-25) — §B2 : quatre
 * informations valent par lot ({@code parLot}), saisies sous {@code CODE#n} sur une ligne allotie, exigées pour chaque
 * lot au bilan, et l'acte d'engagement est produit une fois par lot ; §B1, §B3, §B4 : le référentiel des fournitures.
 *
 * <p>Jeu : plan 9900 (PRMP001, ANT, CLOTURE, PV signé FAV), lignes à commande en appel d'offres ouvert, nature
 * fournitures : 9901 non allotie, 9902 en trois lots au plan.</p>
 */
class FicheDaoParLotIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired private ChampFicheMarcheService champService;
    @Autowired private cnm.prs.repository.ChampFicheMarcheRepository champRepository;

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
        ligne(9901);
        ligne(9902);
        for (int n = 1; n <= 3; n++) {
            Lot lot = new Lot();
            lot.setIdLot(9910 + n);
            lot.setIdDossier(9900);
            lot.setIdDetail(9902);
            lot.setDesignationLot("Matériels informatiques, lot " + n);
            lotRepository.save(lot);
        }
        assertThat(champService.importerCsv(new ClassPathResource(
                "fiche-marche/referentiel-champs-fiche-marche-fournitures.csv").getFile().toPath()).rejets()).isEmpty();
    }

    @Test
    @DisplayName("1 — Référentiel à commande : parLot servi, vrai pour les quatre champs et, depuis V45, le lieu de livraison ; B02-AU-04 repris dans "
            + "l'AE ; B06-EO-11 réservé à la quantité fixe ; les cinq informations créées sont servies")
    void referentiel() throws Exception {
        String ref = referentiel("A_COMMANDE");
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[?(@.parLot==true)].code"))
                .containsExactlyInAnyOrder("B05-GS-03", "B05-TP-02", "B05-TP-03", "B06-EO-12", "B09-LL-01");
        assertThat(JsonPath.<List<Boolean>>read(ref, "$.champs[?(@.code=='B02-AU-04')].parLot")).containsExactly(false);
        assertThat(JsonPath.<List<List<String>>>read(ref, "$.champs[?(@.code=='B02-AU-04')].reprises"))
                .containsExactly(List.of("AE"));
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[*].code"))
                .contains("B02-AU-07", "B02-OB-03", "B03-NA-03", "B04-RO-03", "B09-PC-02", "B06-EO-12")
                .doesNotContain("B06-EO-11");
        assertThat(JsonPath.<List<String>>read(referentiel("QUANTITE_FIXE"), "$.champs[*].code"))
                .contains("B06-EO-11").doesNotContain("B06-EO-12");
    }

    @Test
    @DisplayName("2 — Ligne en trois lots : nbLots 3, saisieParLot ; CODE#n enregistré, en lettres par lot ; le bilan exige "
            + "chaque lot manquant, rang au message ; clé nue, rang hors du plan, rang sur un champ commun : 400 nominatif")
    void saisieParLot() throws Exception {
        Long idDmc = creerDmc(9902);
        mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbLots").value(3))
                .andExpect(jsonPath("$.saisieParLot").value(true));

        String fiche = bloc(idDmc, "B05", "{\"B05-TP-02#1\":\"1600000\",\"B05-TP-02#2\":\"2170000\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(fiche, "$.valeurs['B05-TP-02#1']")).isEqualTo("1600000");
        assertThat(JsonPath.<String>read(fiche, "$.valeurs['B05-TP-02#2']")).isEqualTo("2170000");
        assertThat(JsonPath.<String>read(fiche, "$.enLettres['B05-TP-02#2']")).containsIgnoringCase("deux millions");
        List<String> manquants = JsonPath.read(fiche, "$.bilanControles.bloquants[?(@.regle=='OBLIGATOIRE')].champs[0]");
        assertThat(manquants).contains("B05-TP-02#3", "B05-TP-03#1", "B05-TP-03#2", "B05-TP-03#3")
                .doesNotContain("B05-TP-02", "B05-TP-02#1", "B05-TP-02#2");
        assertThat(JsonPath.<List<String>>read(fiche,
                "$.bilanControles.bloquants[?(@.champs[0]=='B05-TP-02#3')].message"))
                .containsExactly("« Montant minimum annuel du marché (Ariary) » (lot 3) est obligatoire.");

        bloc(idDmc, "B05", "{\"B05-TP-02\":\"1000\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("B05-TP-02"));
        bloc(idDmc, "B05", "{\"B05-TP-02#4\":\"1000\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("B05-TP-02#4"));
        bloc(idDmc, "B02", "{\"B02-AU-04#1\":\"12\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("B02-AU-04#1"));
    }

    @Test
    @DisplayName("3 — Ligne non allotie : clé nue ; CODE#1 vaut la clé nue, CODE#2 est refusé ; un seul acte d'engagement")
    void ligneNonAllotie() throws Exception {
        Long idDmc = creerDmc(9901);
        String fiche = bloc(idDmc, "B05", "{\"B05-TP-02#1\":\"1000000\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.nbLots").value(0))
                .andExpect(jsonPath("$.saisieParLot").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Map<String, String>>read(fiche, "$.valeurs")).containsEntry("B05-TP-02", "1000000")
                .doesNotContainKey("B05-TP-02#1");
        bloc(idDmc, "B05", "{\"B05-TP-02#2\":\"1000\"}").andExpect(status().isBadRequest());

        cadrageCommande(idDmc, "NON");
        remplirObligatoiresEtValider(idDmc, "A_COMMANDE", "FOURNITURES_SERVICES", Map.of());
        assertThat(JsonPath.<List<String>>read(documents(idDmc), "$[*].type"))
                .containsExactly("DPAO", "DPAO", "CCAP", "CCAP", "AE", "AE", "LF", "LF", "BP", "TC");
        assertThat(JsonPath.<List<Object>>read(documents(idDmc), "$[?(@.lot != null)]")).isEmpty();
    }

    @Test
    @DisplayName("4 — Validation d'une ligne en trois lots : un acte d'engagement par lot (libellé, fichier, lot), chacun "
            + "avec les montants de son lot seulement ; le DPAO, commun, porte le délai de chaque lot")
    void unActeDEngagementParLot() throws Exception {
        Long idDmc = creerDmc(9902);
        cadrageCommande(idDmc, "OUI");
        Map<String, String> donnees = new LinkedHashMap<>();
        for (int n = 1; n <= 3; n++) {
            donnees.put("B05-TP-02#" + n, String.valueOf(1000000 * n));
            donnees.put("B05-TP-03#" + n, String.valueOf(5000000 * n));
            donnees.put("B06-EO-12#" + n, String.valueOf(10 * n));
        }
        remplirObligatoiresEtValider(idDmc, "A_COMMANDE", "FOURNITURES_SERVICES", donnees);

        String documents = documents(idDmc);
        assertThat(JsonPath.<List<String>>read(documents, "$[*].type"))
                .containsExactly("DPAO", "DPAO", "CCAP", "CCAP", "AE", "AE", "AE", "AE", "AE", "AE", "LF", "LF", "BP", "BP", "BP", "TC", "TC", "TC");
        assertThat(JsonPath.<List<Integer>>read(documents, "$[?(@.type=='AE')].lot")).containsExactly(1, 1, 2, 2, 3, 3);
        assertThat(JsonPath.<List<String>>read(documents, "$[?(@.type=='AE')].libelle"))
                .contains("Acte d'engagement — lot 1", "Acte d'engagement — lot 3");
        assertThat(JsonPath.<List<String>>read(documents, "$[?(@.type=='AE')].nomFichier"))
                .allMatch(n -> n.matches("AE_.*_9902_lot[123]_v1\\.(docx|pdf)"));

        String ae2 = texte(documents, "AE", 2);
        assertThat(ae2).contains("Acte d'engagement — lot 2", "Montant minimum annuel du marché (Ariary) : 2 000 000 Ariary",
                "Montant maximum annuel du marché (Ariary) : 10 000 000 Ariary")
                .doesNotContain("(Ariary) : 1 000 000 Ariary", "(Ariary) : 3 000 000 Ariary", "15 000 000", "— lot 1", "— lot 3");
        String dpao = texte(documents, "DPAO", null);
        assertThat(dpao).contains("Délai maximum de livraison (jours) — lot 1 : 10",
                "Délai maximum de livraison (jours) — lot 3 : 30");
    }

    @Test
    @DisplayName("5 — Administration : seul un champ saisi vaut par lot (400 nominatif sur une reprise du plan) ; un fichier "
            + "sans colonne parLot ne l'efface pas")
    void parLotReserveALaSaisie() throws Exception {
        ChampFicheMarcheDto ppm = ChampFicheMarcheService.toDto(champRepository.findById("B02-OB-01").orElseThrow());
        ppm.setParLot(true);
        assertThatThrownBy(() -> champService.modifier("B02-OB-01", ppm)).isInstanceOf(ChampsInvalidesException.class)
                .satisfies(e -> assertThat(((ChampsInvalidesException) e).getErreurs())
                        .anyMatch(f -> f.champ().equals("parLot")));

        ChampFicheMarcheDto tp = ChampFicheMarcheService.toDto(champRepository.findById("B05-TP-02").orElseThrow());
        tp.setParLot(null);
        champService.modifier("B05-TP-02", tp);
        assertThat(champRepository.findById("B05-TP-02").orElseThrow().getParLot()).isTrue();
    }

    // ------------------------------------------------------------------ outils

    private String referentiel(String type) throws Exception {
        return mvc.perform(get("/api/champs-fiche-marche").param("typeMarche", type)
                .param("categorie", "FOURNITURES_SERVICES").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private org.springframework.test.web.servlet.ResultActions bloc(Long idDmc, String bloc, String valeurs) throws Exception {
        return mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/" + bloc).header("Authorization", tokenPrmp)
                .contentType(JSON).content("{\"valeurs\":" + valeurs + "}"));
    }

    private void cadrageCommande(Long idDmc, String alloti) throws Exception {
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"alloti\":\"" + alloti + "\",\"variantes\":\"NON\",\"groupement\":\"NON\","
                        + "\"provenance\":\"NATIONAL\",\"typePrix\":\"UNITAIRES\",\"prixRevisable\":\"NON\","
                        + "\"garantieSoumission\":\"NON\",\"avance\":\"NON\",\"penalites\":\"CCAG\"}}"))
                .andExpect(status().isOk());
    }

    private String documents(Long idDmc) throws Exception {
        return mvc.perform(get("/api/fiches-marche/" + idDmc + "/documents").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String texte(String documents, String type, Integer lot) throws Exception {
        String filtre = "$[?(@.type=='" + type + "' && @.extension=='docx'"
                + (lot == null ? "" : " && @.lot==" + lot) + ")].idDocument";
        int id = JsonPath.<List<Integer>>read(documents, filtre).get(0);
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

    private void ligne(int idDetail) {
        Marche l = marcheDao(idDetail, 9900, 9900);
        l.setIdMode(92);
        l.setFormeMarche(FormeMarche.A_COMMANDE);
        l.setDesignationMarche("Acquisition de matériels informatiques " + idDetail);
        marcheRepository.save(l);
    }
}
