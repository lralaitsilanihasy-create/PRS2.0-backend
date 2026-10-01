package cnm.prs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * ⚠️ <strong>Le compte bancaire unique de l'ARMP</strong> sur lequel se verse le prix du DAO (décision du pilote du
 * 2026-10-01, demande front « avis spécifique » §B8.2) — {@code GET/PUT /api/parametres/compte-dao} : lecture par
 * l'Administrateur, la PRMP et l'UGPM ; réglage par l'Administrateur seul, les trois informations exigées.
 */
class ParametreCompteDaoIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Test
    @DisplayName("Non réglé : champs nuls ; PUT hors Administrateur → 403 ; PUT incomplet → 400 nominatif ; PUT complet → 200, "
            + "relu avec la date et l'acteur ; lecture PRMP permise, Commission refusée")
    void compteDao() throws Exception {
        mvc.perform(get("/api/parametres/compte-dao").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.banque").isEmpty()).andExpect(jsonPath("$.misAJourLe").isEmpty());
        mvc.perform(get("/api/parametres/compte-dao").header("Authorization", tokenMembre)).andExpect(status().isForbidden());
        String corps = "{\"banque\":\"BNI Madagascar\",\"titulaire\":\"ARMP\",\"numeroCompte\":\"00005 00001 12345678901 23\"}";
        mvc.perform(put("/api/parametres/compte-dao").header("Authorization", tokenPrmp).contentType(JSON).content(corps))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/parametres/compte-dao").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"banque\":\"BNI Madagascar\",\"titulaire\":\" \"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='titulaire')]").isNotEmpty())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='numeroCompte')]").isNotEmpty())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='banque')]").isEmpty());
        mvc.perform(put("/api/parametres/compte-dao").header("Authorization", tokenAdmin).contentType(JSON).content(corps))
                .andExpect(status().isOk()).andExpect(jsonPath("$.numeroCompte").value("00005 00001 12345678901 23"));
        mvc.perform(get("/api/parametres/compte-dao").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.banque").value("BNI Madagascar")).andExpect(jsonPath("$.titulaire").value("ARMP"))
                .andExpect(jsonPath("$.misAJourLe").isNotEmpty());
    }
}
