package cnm.prs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * ⚠️ R3a (manuel révisé pour l'application, lot 1, §B3 ; arbitrage Q3 du pilote du 10/10) — {@code GET/PUT /api/parametres/sigmp-transition} :
 * la date de fin de la transition SIGMP et la décision formelle d'équivalence ; ce qu'en déduisent les documents (pied) et les listes
 * de pièces (transition en cours).
 */
class ParametreSigmpTransitionIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Test
    @DisplayName("Non réglé : transition en cours, pied « Projet — équivalence SIGMP en attente » ; PUT hors Administrateur → 403 ; "
            + "400 nominatif (date illisible, décision incomplète) ; réglé : pied avec la décision, transition terminée dès la date "
            + "atteinte ; effacement par des valeurs nulles")
    void sigmpTransition() throws Exception {
        mvc.perform(get("/api/parametres/sigmp-transition").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.finTransition").isEmpty()).andExpect(jsonPath("$.decisionReference").isEmpty())
                .andExpect(jsonPath("$.transitionEnCours").value(true))
                .andExpect(jsonPath("$.pied").value("Projet — équivalence SIGMP en attente"));
        mvc.perform(get("/api/parametres/sigmp-transition").header("Authorization", tokenMembre)).andExpect(status().isOk());

        mvc.perform(put("/api/parametres/sigmp-transition").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"finTransition\":\"2027-01-01\"}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/parametres/sigmp-transition").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"finTransition\":\"01/01/2027\",\"decisionReference\":\"12/CNM/2026\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='finTransition')]").isNotEmpty())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='decisionDate')]").isNotEmpty());
        mvc.perform(put("/api/parametres/sigmp-transition").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"decisionDate\":\"2026-10-01\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='decisionReference')]").isNotEmpty());

        // Réglé, date de fin à venir : la transition dure, le pied porte la décision.
        mvc.perform(put("/api/parametres/sigmp-transition").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"finTransition\":\"2999-12-31\",\"decisionReference\":\"12/CNM/2026\",\"decisionDate\":\"2026-10-01\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.finTransition").value("2999-12-31"))
                .andExpect(jsonPath("$.decisionReference").value("12/CNM/2026")).andExpect(jsonPath("$.decisionDate").value("2026-10-01"))
                .andExpect(jsonPath("$.transitionEnCours").value(true))
                .andExpect(jsonPath("$.pied").value("Document produit par la plateforme PRS 2.0 en application de la décision n° 12/CNM/2026 du 01/10/2026"));
        // Date de fin atteinte (aujourd'hui) : terminée.
        mvc.perform(put("/api/parametres/sigmp-transition").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"finTransition\":\"" + java.time.LocalDate.now() + "\",\"decisionReference\":\"12/CNM/2026\",\"decisionDate\":\"2026-10-01\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.transitionEnCours").value(false));
        // Effacement.
        mvc.perform(put("/api/parametres/sigmp-transition").header("Authorization", tokenAdmin).contentType(JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.finTransition").isEmpty())
                .andExpect(jsonPath("$.transitionEnCours").value(true))
                .andExpect(jsonPath("$.pied").value("Projet — équivalence SIGMP en attente"));
    }
}
