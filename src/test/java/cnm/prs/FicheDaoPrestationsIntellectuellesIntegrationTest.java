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
    @DisplayName("1 — Import : 90 champs, aucun rejet (DPIC admis) ; référentiel des prestations intellectuelles : 23 "
            + "informations du plan + 88 actives, ses rubriques seulement, pas de B07 ni de B11 ; DPIC refusé nulle part")
    void chargement() throws Exception {
        assertThat(bilan.rejets()).isEmpty();
        assertThat(bilan.crees()).hasSize(90);
        String ref = mvc.perform(get("/api/champs-fiche-marche?typeMarche=QUANTITE_FIXE&categorie=PRESTATIONS_INTELLECTUELLES")
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[*].code")).hasSize(23 + 88);
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[?(@.documentMaitre=='DPIC')].code")).isNotEmpty();
        assertThat(JsonPath.<List<String>>read(ref, "$.blocs[*].code")).doesNotContain("B07", "B11");
        assertThat(JsonPath.<List<String>>read(ref, "$.blocs[*].rubriques[*].code")).contains("B02-CL", "B02-MS", "B01-AC")
                .doesNotContain("B02-AU", "B02-LT");
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
    }
}
