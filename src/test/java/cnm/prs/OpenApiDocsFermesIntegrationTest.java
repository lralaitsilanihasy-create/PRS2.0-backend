package cnm.prs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * ⚠️ Audit 2026-08-27 (lot E, constat « Swagger UI / OpenAPI publics ») — la documentation générée
 * était en {@code permitAll} <strong>inconditionnel</strong> : n'importe qui sur le réseau obtenait
 * la carte complète de l'API (toutes les routes, tous les schémas de corps), ce qui est un point de
 * départ commode pour chercher les faiblesses. Elle est désormais pilotée par
 * {@code app.docs.publics}.
 *
 * <p>Cette classe couvre la valeur attendue <strong>en production</strong> ({@code false}) ; le
 * défaut ({@code true}, pratique en développement) reste couvert par
 * {@code OpenApiDocsIntegrationTest}. Contexte Spring distinct : la propriété est lue au montage de
 * la chaîne de filtres, elle ne peut pas changer d'un test à l'autre.</p>
 */
@SpringBootTest(properties = "app.docs.publics=false")
class OpenApiDocsFermesIntegrationTest extends CnmIntegrationTestSupport {

    @Test
    @DisplayName("app.docs.publics=false : la documentation n'est plus servie à un anonyme (401)")
    void docsFermes_anonymeRefuse() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("app.docs.publics=false : refusée à un profil non-Administrateur (403), servie à l'Administrateur")
    void docsFermes_administrateurSeulement() throws Exception {
        // Authentifie mais sans le profil : 403, la doc n'est pas une ressource ouverte a tous.
        mvc.perform(get("/v3/api-docs").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());

        // L'Administrateur la consulte toujours : la propriete restreint, elle ne supprime pas.
        mvc.perform(get("/v3/api-docs").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths./api/dossiers").exists());
    }
}
