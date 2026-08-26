package cnm.prs.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration CORS des endpoints {@code /api/**}.
 *
 * <p>⚠️ Depuis la bascule même-origine (2026-08-17), le frontend appelle l'API en relatif
 * via le proxy du serveur de dev : il n'y a plus de requête cross-origin en développement.
 * Cette configuration ne sert plus qu'aux outils externes explicitement autorisés — les
 * origines sont désormais portées par la propriété {@code app.cors.allowed-origins}
 * (liste séparée par des virgules) au lieu d'être codées en dur.</p>
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public CorsConfig(@Value("${app.cors.allowed-origins:http://localhost:4200}") String allowedOrigins) {
        this.allowedOrigins = allowedOrigins.split(",");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
