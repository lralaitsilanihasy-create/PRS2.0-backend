package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import cnm.prs.config.HorlogeRecette;

/**
 * ⚠️ 2026-10-24 (demande front « horloge-recette », R1) — hors recette (le défaut, et la production), l'horloge est celle du
 * système et la route n'existe pas.
 */
class HorlogeRecetteInactiveIntegrationTest extends CnmIntegrationTestSupport {

    @Autowired
    private Clock clock;

    @Test
    @DisplayName("Hors recette : bean = horloge système, route absente (404) même pour l'Administrateur")
    void inactive() throws Exception {
        assertThat(clock).isNotSameAs(HorlogeRecette.HORLOGE);
        assertThat(clock.getClass().getName()).doesNotContain("HorlogeRecette");
        mvc.perform(get("/api/admin/horloge-recette").header("Authorization", tokenAdmin)).andExpect(status().isNotFound());
    }
}
