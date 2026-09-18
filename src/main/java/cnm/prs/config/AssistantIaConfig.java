package cnm.prs.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Branche les réglages {@code app.ia.*} de l'assistant IA local ({@link AssistantIaProperties}).
 *
 * <p>Volontairement AUCUN bean d'exécuteur ici : déclarer un {@code TaskExecutor} désactiverait
 * l'exécuteur auto-configuré de Spring Boot, dont dépendent les méthodes {@code @Async} de
 * l'application. L'assistant garde son propre pool, privé, dans {@code AssistantIaService}.</p>
 */
@Configuration
@EnableConfigurationProperties(AssistantIaProperties.class)
public class AssistantIaConfig {
}
