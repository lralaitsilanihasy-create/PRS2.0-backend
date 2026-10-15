package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.Capm;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Marche;
import cnm.prs.entity.MarchePrevision;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.TypeDmc;

/**
 * ⚠️ R6a (manuel révisé pour l'application, lot 2, §B1 ; arbitrage Q6 du pilote ; V106) — les neuf délais de la loi servis, les délais
 * administrables lus et réglés par l'Administrateur, et le délai de publicité au bilan de la fiche : bloquant pour l'appel d'offres
 * ouvert (30 jours entre la prévision de lancement du plan et la date limite), signalé pour l'appel d'offres restreint (paramètre),
 * muet quand le paramètre est désactivé.
 */
class DelaisLegauxIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final LocalDate LANCEMENT = LocalDate.of(2026, 3, 2);

    @BeforeEach
    void jeu() throws Exception {
        TypeDmc dao = typeDmcRepository.findByCode("DAO").orElseThrow();
        ModePassation m92 = new ModePassation(92, "Appel d'offres ouvert", null, null, null, null);
        m92.setIdTypeDmc(dao.getIdTypeDmc());
        modePassationRepository.save(m92);
        ModePassation m93 = new ModePassation(93, "Appel d'offres restreint", null, null, null, null);
        m93.setIdTypeDmc(dao.getIdTypeDmc());
        modePassationRepository.save(m93);
        dossierRepository.save(dossierLoc(9900, "CLOTURE", "ANT", "PRMP001"));
        Dossier plan = dossierRepository.findById(9900).orElseThrow();
        plan.setIdEntiteContract(1);
        dossierRepository.save(plan);
        ppmRepository.save(ppm(9900, 9900, "PRMP001"));
        receptionRepository.save(reception(9900, 9900, "CTRCC1", true));
        dispatchRepository.save(dispatch(9900, 9900, "CTRCC1", "CTRMEM", "CTRPRE"));
        examenRepository.save(examen(9900, 9900, "CTRMEM"));
        seedPvSigne(9900, 9900);
        Marche ouvert = marcheDao(9901, 9900, 9900);
        ouvert.setIdMode(92);
        marcheRepository.save(ouvert);
        Marche restreint = marcheDao(9902, 9900, 9900);
        restreint.setIdMode(93);
        marcheRepository.save(restreint);
        capmRepository.save(new Capm(9901, "Lancement de l'appel d'offres", 1, 92, null));
        marchePrevisionRepository.save(new MarchePrevision(9901, 9901, 9901, LANCEMENT, LANCEMENT, null, null));
        marchePrevisionRepository.save(new MarchePrevision(9902, 9902, 9901, LANCEMENT, LANCEMENT, null, null));
        assertThat(champService.importerCsv(new org.springframework.core.io.ClassPathResource(
                "fiche-marche/referentiel-champs-fiche-marche-fournitures.csv").getFile().toPath()).rejets()).isEmpty();
    }

    @org.springframework.beans.factory.annotation.Autowired private cnm.prs.service.ChampFicheMarcheService champService;

    @Test
    @DisplayName("GET /api/delais-legaux : les onze délais de la loi et du décret ; GET /api/delais-parametres : les six délais semés (le contrat-cadre désactivé, V121) ; PUT par "
            + "l'Administrateur seul (403 PRMP), 404 code inconnu, 400 valeur négative ou unité inconnue, un champ absent garde sa valeur")
    void delais() throws Exception {
        String legaux = mvc.perform(get("/api/delais-legaux").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(legaux, "$[*].numero")).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);   // 15/10 : + décret 2019-1064, art. 10
        assertThat(JsonPath.<List<String>>read(legaux, "$[?(@.bloquant==true)].code")).contains("PUBLICITE_AOO_NATIONAL", "ATTENTE_SIGNATURE")
                .doesNotContain("AVIS_ATTRIBUTION");
        String parametres = mvc.perform(get("/api/delais-parametres").header("Authorization", tokenMembre)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(parametres, "$[*].code")).contains("PUBLICITE_AOR", "PUBLICITE_DC_PI", "PUBLICITE_AMI",
                "PUBLICITE_CONTRAT_CADRE", "PUBLICITE_PREQUALIFICATION", "DELAI_AMENAGE");
        // ⚠️ V121 (15/10) — le paramètre du contrat-cadre est désactivé : le décret 2019-1064 (art. 10) l'a remplacé.
        assertThat(JsonPath.<List<Boolean>>read(parametres, "$[?(@.code=='PUBLICITE_CONTRAT_CADRE')].actif")).containsExactly(false);
        assertThat(JsonPath.<List<Boolean>>read(parametres, "$[?(@.code!='PUBLICITE_CONTRAT_CADRE')].actif")).containsOnly(true);

        mvc.perform(put("/api/delais-parametres/PUBLICITE_AOR").header("Authorization", tokenPrmp).contentType(JSON).content("{\"valeur\":20}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/delais-parametres/INCONNU").header("Authorization", tokenAdmin).contentType(JSON).content("{\"valeur\":20}"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/delais-parametres/PUBLICITE_AOR").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"valeur\":-1,\"unite\":\"SEMAINES\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='valeur')]").isNotEmpty()).andExpect(jsonPath("$.erreurs[?(@.champ=='unite')]").isNotEmpty());
        mvc.perform(put("/api/delais-parametres/publicite_aor").header("Authorization", tokenAdmin).contentType(JSON).content("{\"valeur\":20}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("PUBLICITE_AOR")).andExpect(jsonPath("$.valeur").value(20))
                .andExpect(jsonPath("$.unite").value("JOURS")).andExpect(jsonPath("$.actif").value(true))
                .andExpect(jsonPath("$.imActeur").isNotEmpty());
    }

    @Test
    @DisplayName("Au bilan de la fiche : appel d'offres ouvert, date limite à 20 jours de la prévision de lancement → DELAI_PUBLICITE "
            + "bloquant (409 CONTROLES_BLOQUANTS à la validation), à 30 jours → conforme et validée ; appel d'offres restreint → avertissement "
            + "du paramètre (15 j), muet une fois le paramètre désactivé")
    void auBilan() throws Exception {
        Long ouvert = creerDmc(9901);
        cadrage(ouvert);
        limite(ouvert, LANCEMENT.plusDays(20));
        String bilan = controler(ouvert);
        assertThat(JsonPath.<List<String>>read(bilan, "$.bloquants[*].regle")).contains("DELAI_PUBLICITE");
        assertThat(JsonPath.<List<String>>read(bilan, "$.bloquants[?(@.regle=='DELAI_PUBLICITE')].message").get(0))
                .contains("20 jour(s)", "prévision de lancement du plan", "30 jour(s) requis", "art. 35-II");
        assertThat(JsonPath.<List<List<String>>>read(bilan, "$.bloquants[?(@.regle=='DELAI_PUBLICITE')].champs").get(0)).containsExactly("B04-LR-03");
        mvc.perform(post("/api/fiches-marche/" + ouvert + "/valider").header("Authorization", tokenPrmp)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTROLES_BLOQUANTS"));
        limite(ouvert, LANCEMENT.plusDays(30));
        bilan = controler(ouvert);
        assertThat(JsonPath.<List<String>>read(bilan, "$.bloquants[*].regle")).doesNotContain("DELAI_PUBLICITE");
        assertThat(JsonPath.<List<String>>read(bilan, "$.ok[*].regle")).contains("DELAI_PUBLICITE");
        mvc.perform(post("/api/fiches-marche/" + ouvert + "/valider").header("Authorization", tokenPrmp)).andExpect(status().isOk());

        Long restreint = creerDmc(9902);
        cadrage(restreint);
        limite(restreint, LANCEMENT.plusDays(5));
        bilan = controler(restreint);
        assertThat(JsonPath.<List<String>>read(bilan, "$.bloquants[*].regle")).doesNotContain("DELAI_PUBLICITE");
        assertThat(JsonPath.<List<String>>read(bilan, "$.avertissements[?(@.regle=='DELAI_PUBLICITE')].message").get(0))
                .contains("5 jour(s)", "15 jour(s) requis", "à confirmer par le pilote");
        mvc.perform(put("/api/delais-parametres/PUBLICITE_AOR").header("Authorization", tokenAdmin).contentType(JSON).content("{\"actif\":false}"))
                .andExpect(status().isOk());
        bilan = controler(restreint);
        assertThat(JsonPath.<List<String>>read(bilan, "$.avertissements[*].regle")).doesNotContain("DELAI_PUBLICITE");
        assertThat(JsonPath.<List<String>>read(bilan, "$.ok[*].regle")).doesNotContain("DELAI_PUBLICITE");
    }

    // ------------------------------------------------------------------ outils

    private Long creerDmc(int idDetail) throws Exception {
        String corps = mvc.perform(post("/api/dmcs/par-marche/" + idDetail).header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(corps, "$.idDmc")).longValue();
    }

    private void cadrage(Long idDmc) throws Exception {
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"garantieSoumission\":\"NON\",\"alloti\":\"NON\",\"typePrix\":\"UNITAIRES\"}}")).andExpect(status().isOk());
    }

    /** Remplit la fiche (tout obligatoire) avec cette date limite de remise, sans valider. */
    private void limite(Long idDmc, LocalDate date) throws Exception {
        remplirObligatoires(idDmc, "QUANTITE_FIXE", "FOURNITURES_SERVICES", java.util.Map.of("B04-LR-03", date.toString()));
    }

    private String controler(Long idDmc) throws Exception {
        return mvc.perform(post("/api/fiches-marche/" + idDmc + "/controler").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
