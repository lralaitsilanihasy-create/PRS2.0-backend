package cnm.prs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Assistant IA <strong>désactivé</strong> — le réglage par défaut ({@code app.ia.actif=false}) : le
 * front le masque, son API ne fait rien. Même contexte Spring que le reste de la suite.
 */
class AssistantIaInactifIntegrationTest extends CnmIntegrationTestSupport {

    @Test
    @DisplayName("Par défaut l'assistant est inactif : l'état le dit sans nommer de modèle, une question reçoit 404")
    void inactifParDefaut() throws Exception {
        mvc.perform(get("/api/assistant-ia/etat").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actif").value(false))
                .andExpect(jsonPath("$.disponible").value(false))
                .andExpect(jsonPath("$.modele").doesNotExist());
        mvc.perform(post("/api/assistant-ia/questions").header("Authorization", tokenMembre)
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Quel délai ?\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("⚠️ Assistant inactif : la synthèse d'un dossier répond 404 AVANT de lire quoi que ce "
            + "soit — c'est ce 404 qui fait disparaître le bloc de l'écran, plutôt qu'un bouton qui "
            + "échouerait toujours")
    void syntheseInactive_404AvantTouteLecture() throws Exception {
        // L'identifiant n'existe pas, et c'est exprès : si le 404 venait de la lecture du dossier au lieu
        // du réglage, la garde ne serait pas là où on croit.
        mvc.perform(post("/api/assistant-ia/dossiers/999999/synthese").header("Authorization", tokenMembre)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
