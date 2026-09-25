package cnm.prs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import cnm.prs.entity.Dossier;

/**
 * ⚠️ Signalement front du 2026-09-25 — une réception enregistrée sans {@code imCtrlRecept} (un script qui ne l'envoie
 * pas, quand l'écran l'envoie) n'avait pas de réceptionnaire, donc pas de localité : le visa du PV refusait ensuite
 * tous les Membres de la localité (§3.3). Le serveur pose l'acteur courant ; un réceptionnaire fourni est gardé.
 */
class ReceptionReceptionnaireIntegrationTest extends CnmIntegrationTestSupport {

    @Test
    @DisplayName("Corps sans imCtrlRecept → le réceptionnaire est l'acteur courant ; fourni → gardé")
    void receptionnaireParDefautActeurCourant() throws Exception {
        for (int id : new int[] { 7120, 7121 }) {
            Dossier d = dossier(id, "SOUMIS");
            d.setIdTypeDossier("DDP");
            d.setIdPrmp("PRMP001");
            d.setIdLocalite("ANT");
            dossierRepository.save(d);
        }
        mvc.perform(post("/api/receptions").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":7120,\"numPassage\":1,\"typePassage\":\"INITIAL\",\"complet\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imCtrlRecept").value("CTRCC1"));
        mvc.perform(post("/api/receptions").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":7121,\"numPassage\":1,\"typePassage\":\"INITIAL\",\"complet\":true,"
                        + "\"imCtrlRecept\":\"CTRCC1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imCtrlRecept").value("CTRCC1"));
    }
}
