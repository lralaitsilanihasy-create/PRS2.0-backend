package cnm.prs.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.entity.ActionDossier;

/**
 * ⚠️ 2026-10-24 (demande front « horloge-recette », R1 ; ADR-0016) — toute la règle du temps passe par l'horloge : aucun
 * {@code now()} sans horloge dans {@code src/main}, hormis les appels qui gardent l'heure réelle par construction.
 */
class HorlogeUniqueTest {

    /** Gardent l'heure réelle : jetons JWT, sessions de connexion, horodatage des réponses d'erreur, l'horloge elle-même. */
    private static final Set<String> HEURE_REELLE = Set.of("config/HorlogeRecette.java", "security/TokenService.java",
            "service/JournalConnexionService.java", "exception/GlobalExceptionHandler.java",
            "controller/ParametresInternesController.java");
    private static final Pattern NOW = Pattern.compile(
            "\\b(LocalDateTime|LocalDate|LocalTime|Instant|Year|YearMonth|OffsetDateTime|ZonedDateTime)\\.now\\(\\s*\\)");

    @AfterEach
    void heureReelle() {
        if (HorlogeRecette.active()) {
            HorlogeRecette.regler(Duration.ZERO);
        }
    }

    @Test
    @DisplayName("Aucun now() sans horloge hors des appels à l'heure réelle")
    void aucunNowSansHorloge() throws IOException {
        Path racine = Path.of("src/main/java/cnm/prs");
        List<String> fautifs = new ArrayList<>();
        try (Stream<Path> fichiers = Files.walk(racine)) {
            for (Path p : fichiers.filter(f -> f.toString().endsWith(".java")).toList()) {
                String rel = racine.relativize(p).toString().replace('\\', '/');
                if (HEURE_REELLE.contains(rel)) {
                    continue;
                }
                List<String> lignes = Files.readAllLines(p, StandardCharsets.UTF_8);
                for (int i = 0; i < lignes.size(); i++) {
                    if (NOW.matcher(lignes.get(i)).find()) {
                        fautifs.add(rel + ":" + (i + 1));
                    }
                }
            }
        }
        assertThat(fautifs).as("utiliser l'horloge injectée, ou HorlogeRecette.HORLOGE").isEmpty();
    }

    @Test
    @DisplayName("La mention de l'heure réelle : absente sans décalage, tenue dans la colonne sinon")
    void mention() {
        ActionDossier a = new ActionDossier();
        a.setDetail("x".repeat(600));
        assertThat(HorlogeRecette.mention()).isNull();
        assertThat(HorlogeRecette.mentionner("détail", 500)).isEqualTo("détail");

        HorlogeRecette.activer(Duration.ofDays(30));
        assertThat(HorlogeRecette.mention()).startsWith("[recette — heure réelle : ").endsWith("]");
        assertThat(HorlogeRecette.mentionner(null, 500)).isEqualTo(HorlogeRecette.mention());
        a.mentionnerHeureReelle();
        assertThat(a.getDetail()).hasSize(500).endsWith(HorlogeRecette.mention()).contains("x…");
    }

    @Test
    @DisplayName("Le décalage relu au démarrage : absent ou illisible = heure réelle")
    void lireDecalage() throws IOException {
        Path f = Files.createTempFile("horloge", ".txt");
        try {
            Files.writeString(f, "PT792H\n");
            assertThat(ClockConfig.lireDecalage(f)).isEqualTo(Duration.ofHours(792));
            Files.writeString(f, "n'importe quoi");
            assertThat(ClockConfig.lireDecalage(f)).isZero();
            Files.delete(f);
            assertThat(ClockConfig.lireDecalage(f)).isZero();
        } finally {
            Files.deleteIfExists(f);
        }
    }
}
