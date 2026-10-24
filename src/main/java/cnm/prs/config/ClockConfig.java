package cnm.prs.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.format.DateTimeParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ⚠️ Audit 2026-08-27 (lot C) — horloge injectable, seul point d'accès à « aujourd'hui » pour le code
 * qui doit rester testable sans dépendre de la date système (ex. {@link cnm.prs.scheduler.AlerteScheduler}).
 * Bean par défaut : horloge système réelle, fuseau par défaut de la JVM — aucun changement de
 * comportement en production. Les tests remplacent ce bean par une horloge maîtrisée.
 *
 * <p>⚠️ 2026-10-24 (demande front « horloge-recette », R1 ; ADR-0016) — avec {@code app.recette.horloge.active=true}
 * (recette seulement), le bean est {@link HorlogeRecette#HORLOGE}, décalable à chaud par l'Administrateur
 * ({@code /api/admin/horloge-recette}) ; le décalage est relu au démarrage dans {@code app.recette.horloge.fichier}, pour
 * qu'un redémarrage ne ramène pas la recette à l'heure réelle.</p>
 */
@Configuration
public class ClockConfig {

    private static final Logger LOG = LoggerFactory.getLogger(ClockConfig.class);

    @Bean
    public Clock clock(@Value("${app.recette.horloge.active:false}") boolean recette,
            @Value("${app.recette.horloge.fichier:${user.home}/prs-horloge-recette.txt}") String fichier) {
        if (!recette) {
            return Clock.systemDefaultZone();
        }
        Duration decalage = lireDecalage(Path.of(fichier));
        HorlogeRecette.activer(decalage);
        LOG.warn("Horloge de recette ACTIVE : décalage {} (fichier {})", decalage, fichier);
        return HorlogeRecette.HORLOGE;
    }

    /** Le décalage écrit par {@code HorlogeRecetteService} (ISO-8601, « PT792H ») ; nul si le fichier manque ou est illisible. */
    static Duration lireDecalage(Path fichier) {
        try {
            return Files.exists(fichier) ? Duration.parse(Files.readString(fichier, StandardCharsets.UTF_8).trim()) : Duration.ZERO;
        } catch (IOException | DateTimeParseException e) {
            LOG.warn("Décalage de recette illisible dans {} : heure réelle", fichier, e);
            return Duration.ZERO;
        }
    }
}
