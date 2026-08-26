package cnm.prs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contrat d'API généré (springdoc, LOT 5 — 2026-08-26) : garantit que la génération OpenAPI ne
 * casse pas silencieusement (un contrôleur mal annoté la ferait échouer en 500) et que la route
 * reste publique. Modèle : test équivalent du dépôt Collegue.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("OpenAPI : /v3/api-docs se génère (200, sans jeton) et couvre les ressources principales")
    void apiDocs_disponibles_sansJeton() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value(
                        "PRS 2.0 — API de contrôle a priori des marchés publics (CNM)"))
                .andExpect(jsonPath("$.paths./api/dossiers").exists())
                .andExpect(jsonPath("$.paths./api/auth/login").exists())
                .andExpect(jsonPath("$.paths./api/lots").exists());
    }

    @Test
    @DisplayName("OpenAPI : Swagger UI accessible sans jeton (redirection ou page)")
    void swaggerUi_accessible_sansJeton() throws Exception {
        // /swagger-ui.html redirige vers /swagger-ui/index.html (3xx) — l'essentiel : pas de 401.
        mvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }
}
