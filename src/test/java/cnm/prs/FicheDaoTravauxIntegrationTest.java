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
 * ⚠️ <strong>Fiche DAO des travaux et réhabilitation</strong> (demande front du 2026-09-24, référentiel converti) — le
 * bloc B11 et les 96 rubriques de V41, l'import des deux fichiers de correspondance (140 + 117 champs, catégorie
 * TRAVAUX), la catégorie ouverte : une fiche de travaux se prépare, s'ouvre par la question des tranches et produit
 * DPAO, CCAP et AE (DPAC et AE en contrat-cadre). Les fiches de fournitures n'en sont pas changées.
 *
 * <p>Jeu : plan 9900 (PRMP001, ANT, CLOTURE, PV signé FAV), lignes en appel d'offres ouvert : 9901 travaux à quantité
 * fixe, 9902 travaux en contrat-cadre (nature 91 « Travaux »), 9903 fournitures à quantité fixe (nature 92).</p>
 */
class FicheDaoTravauxIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired private ChampFicheMarcheService champService;

    private ChampFicheMarcheService.BilanImport travaux;
    private ChampFicheMarcheService.BilanImport travauxCc;

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
        natureRepository.save(new Nature(91, "Travaux", null, "TRAVAUX"));
        ligne(9901, 91, FormeMarche.QUANTITE_FIXE);
        ligne(9902, 91, FormeMarche.CONTRAT_CADRE);
        ligne(9903, natureFournitures(), FormeMarche.QUANTITE_FIXE);

        importer("referentiel-champs-fiche-marche-fournitures.csv");
        travaux = importer("referentiel-champs-fiche-dao-travaux.csv");
        travauxCc = importer("referentiel-champs-fiche-dao-travaux-contrat-cadre.csv");
    }

    @Test
    @DisplayName("1 — Import : 140 et 117 champs de travaux, aucun rejet ; B11 « Annexes et formulaires » servi aux travaux "
            + "seulement ; aucune rubrique des travaux dans une fiche de fournitures, ni l'inverse")
    void chargement() throws Exception {
        assertThat(travaux.rejets()).isEmpty();
        assertThat(travaux.crees()).hasSize(140);
        assertThat(travauxCc.rejets()).isEmpty();
        assertThat(travauxCc.crees()).hasSize(117);

        String qf = ref("typeMarche=QUANTITE_FIXE&categorie=TRAVAUX");
        assertThat(JsonPath.<List<String>>read(qf, "$.blocs[?(@.code=='B11')].libelle")).containsExactly("Annexes et formulaires");
        assertThat(JsonPath.<List<String>>read(qf, "$.blocs[?(@.code=='B11')].rubriques[*].code"))
                .containsExactly("B11-AN", "B11-FR");
        List<String> rubriquesQf = JsonPath.read(qf, "$.blocs[*].rubriques[*].code");
        assertThat(rubriquesQf).contains("B02-LT", "B09-RP", "B01-AC").doesNotContain("B02-AU", "B04-RO", "B02-DK");
        assertThat(JsonPath.<List<List<String>>>read(qf, "$.champs[?(@.source=='SAISIE')].categories")).allMatch(c -> c.contains("TRAVAUX"));
        // 2026-09-25 (§B3) — la composition du dossier est réemployée par les fournitures, les plans restent aux travaux.
        assertThat(JsonPath.<List<java.util.Map<String, Object>>>read(qf, "$.champs[?(@.source=='SAISIE')]").stream()
                .filter(c -> ((List<?>) c.get("categories")).contains("FOURNITURES_SERVICES")).map(c -> c.get("code")))
                .containsExactlyInAnyOrder("B04-CD-01", "B04-CD-02", "B02-OB-03", "B03-CQ-01", "B03-CQ-09", "B03-CQ-10");   // + V47 : les champs des formulaires du candidat, trois catégories

        String cc = ref("typeMarche=CONTRAT_CADRE&categorie=TRAVAUX");
        assertThat(JsonPath.<List<String>>read(cc, "$.blocs[?(@.code=='B07')].rubriques[*].code")).contains("B07-AT", "B07-DT")
                .doesNotContain("B07-PS");
        String fs = ref("typeMarche=QUANTITE_FIXE&categorie=FOURNITURES_SERVICES");
        assertThat(JsonPath.<List<String>>read(fs, "$.blocs[*].code")).doesNotContain("B11");
        assertThat(JsonPath.<List<String>>read(fs, "$.champs[*].code")).hasSize(148)   // 146 des fournitures + B04-CD-01, -02 (2026-09-25)
                .contains("B04-CD-01", "B04-CD-02").doesNotContain("B04-CD-03");
    }

    @Test
    @DisplayName("2 — Travaux à quantité fixe : outillés, la question des tranches ouvre la tranche ferme et les "
            + "conditionnelles, validation → DPAO, CCAP et AE ; les formulaires à remplir ne bloquent pas")
    void travauxQuantiteFixe() throws Exception {
        String eligibles = mvc.perform(get("/api/dmcs/eligibles").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Boolean>>read(eligibles, "$[?(@.idDetail==9901)].categorieOutillee")).containsExactly(true);
        long idDmc = creerDmc(9901);
        mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categorie").value("TRAVAUX"))
                .andExpect(jsonPath("$.typeOutille").value(true));

        cadrage(idDmc, "{\"tranches\":\"NON\",\"groupement\":\"NON\",\"avance\":\"NON\",\"garantieSoumission\":\"NON\"}");
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B02").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":{\"B02-LT-03\":\"Tranche ferme : gros œuvre\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valeurs.B02-LT-03").doesNotExist());
        cadrage(idDmc, "{\"tranches\":\"OUI\",\"groupement\":\"NON\",\"avance\":\"NON\",\"garantieSoumission\":\"NON\"}");
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B02").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":{\"B02-LT-03\":\"Tranche ferme : gros œuvre\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valeurs.B02-LT-03").value("Tranche ferme : gros œuvre"));

        remplirObligatoiresEtValider(idDmc, "QUANTITE_FIXE", "TRAVAUX", Map.of("B02-LT-03", "Tranche ferme : gros œuvre"));
        List<String> types = JsonPath.read(documents(idDmc), "$[*].type");
        assertThat(types).containsExactly("DPAO", "DPAO", "CCAP", "CCAP", "AE", "AE", "A1", "A1");   // V46 : fiche A1 exigée (B04-CD-01), gabarit provisoire
    }

    @Test
    @DisplayName("3 — Travaux en contrat-cadre : validation → DPAC et AE seulement, jamais DPAO ni CCAP")
    void travauxContratCadre() throws Exception {
        long idDmc = creerDmc(9902);
        cadrage(idDmc, "{\"attributaires\":\"MONO\",\"groupement\":\"NON\",\"avance\":\"NON\"}");
        remplirObligatoiresEtValider(idDmc, "CONTRAT_CADRE", "TRAVAUX", Map.of());
        List<String> types = JsonPath.read(documents(idDmc), "$[*].type");
        assertThat(types).containsExactly("DPAC", "DPAC", "AE", "AE");
    }

    @Test
    @DisplayName("4 — Fiche de fournitures, référentiel des travaux chargé : la question des tranches reste refusée, et "
            + "l'allotissement se valide par son reflet des fournitures (OUI/NON), pas par celui des travaux")
    void fournituresInchangees() throws Exception {
        long idDmc = creerDmc(9903);
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"tranches\":\"OUI\"}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erreurs[0].champ").value("tranches"));
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"alloti\":\"PEUT-ETRE\"}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erreurs[0].champ").value("alloti"));
    }

    // ------------------------------------------------------------------ outils

    private ChampFicheMarcheService.BilanImport importer(String fichier) throws Exception {
        return champService.importerCsv(new ClassPathResource("fiche-marche/" + fichier).getFile().toPath());
    }

    private String ref(String requete) throws Exception {
        return mvc.perform(get("/api/champs-fiche-marche?" + requete).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private long creerDmc(int idDetail) throws Exception {
        String corps = mvc.perform(post("/api/dmcs/par-marche/" + idDetail).header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(corps, "$.idDmc")).longValue();
    }

    private void cadrage(long idDmc, String cadrage) throws Exception {
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":" + cadrage + "}")).andExpect(status().isOk());
    }

    private String documents(long idDmc) throws Exception {
        return mvc.perform(get("/api/fiches-marche/" + idDmc + "/documents").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private void ligne(int idDetail, int idNature, FormeMarche forme) {
        Marche l = marche(idDetail, 9900, 9900);
        l.setIdMode(92);
        l.setFormeMarche(forme);
        l.setIdNature(idNature);
        l.setDesignationMarche("Réhabilitation " + idDetail);
        marcheRepository.save(l);
    }
}
