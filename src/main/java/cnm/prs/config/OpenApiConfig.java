package cnm.prs.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Contrat d'API généré (springdoc / OpenAPI 3) — LOT 5, 2026-08-26.
 *
 * <p>Consultation : Swagger UI sur {@code /swagger-ui.html}, JSON sur {@code /v3/api-docs}
 * (routes publiques, cf. SecurityConfig). Le document {@code docs/api-endpoints.md} reste la
 * référence <strong>narrative</strong> (règles de gestion, périmètres, cas limites) ; le contrat
 * généré, lui, est toujours exact sur les chemins, verbes et formes des DTO — il ne peut pas
 * diverger du code.</p>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI prsOpenApi() {
        // Deux transports pour LE MÊME JWT (cf. plan cookie HttpOnly) : le cookie PRS_SESSION
        // (navigateur) et l'en-tête Authorization: Bearer (clients API, tests). Swagger UI
        // utilise le second via le bouton « Authorize ».
        SecurityScheme bearer = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Jeton JWT émis par POST /api/auth/login (claims : role, acteurType, ref, localite). "
                        + "Dans un navigateur, la session passe par le cookie HttpOnly PRS_SESSION — "
                        + "ce schéma Bearer sert aux clients API.");
        return new OpenAPI()
                .info(new Info()
                        .title("PRS 2.0 — API de contrôle a priori des marchés publics (CNM)")
                        .version("0.0.1-SNAPSHOT")
                        .description("API REST du backend PRS20. Périmètres et règles de gestion : "
                                + "docs/regles-gestion.md ; contrat narratif détaillé : docs/api-endpoints.md."))
                .components(new Components().addSecuritySchemes("bearer-jwt", bearer))
                .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"));
    }
}
