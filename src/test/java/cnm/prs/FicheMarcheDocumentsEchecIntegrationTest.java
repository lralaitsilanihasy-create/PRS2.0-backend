package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.Dossier;
import cnm.prs.entity.Marche;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.TypeDmc;
import cnm.prs.service.DocumentFicheModele;
import cnm.prs.service.GenerateurDocumentsFiche;

/**
 * ⚠️ <strong>Documents de la fiche marché, lot 2a — cas 8 de la recette</strong> : un échec de génération annule la
 * validation (500 nommé {@code GENERATION_DOCUMENTS}), la fiche reste en brouillon et aucun document n'est écrit. Classe
 * à part : le générateur y est remplacé par un double qui échoue (contexte Spring distinct).
 */
class FicheMarcheDocumentsEchecIntegrationTest extends CnmIntegrationTestSupport {

    @MockitoBean private GenerateurDocumentsFiche generateur;

    @Test
    @DisplayName("8 — Génération en échec → 500 GENERATION_DOCUMENTS nommant le document, fiche restée BROUILLON, aucun "
            + "document")
    void echecAnnuleLaValidation() throws Exception {
        when(generateur.generer(any(DocumentFicheModele.class))).thenThrow(new IllegalStateException("gabarit illisible"));

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
        Marche l = marcheDao(9901, 9900, 9900);
        l.setIdMode(92);
        marcheRepository.save(l);

        String dmc = mvc.perform(post("/api/dmcs/par-marche/9901").header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long idDmc = ((Number) JsonPath.read(dmc, "$.idDmc")).longValue();
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"cadrage\":{\"garantieSoumission\":\"NON\"}}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/fiches-marche/" + idDmc + "/valider").header("Authorization", tokenPrmp))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("GENERATION_DOCUMENTS"))
                .andExpect(jsonPath("$.message", containsString("Données particulières de l'appel d'offres")))
                .andExpect(jsonPath("$.message", containsString("gabarit illisible")));

        mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("BROUILLON"))
                .andExpect(jsonPath("$.dateValidation").doesNotExist());
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/documents").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        assertThat(jdbcTemplate.queryForObject("select count(*) from t_document_fiche_marche", Integer.class)).isZero();
    }
}
