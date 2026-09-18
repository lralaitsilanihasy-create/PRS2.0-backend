package cnm.prs.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Libellés du corpus de l'assistant IA dans le VRAI {@code application.properties}.
 *
 * <p>⚠️ Recette du 2026-09-18 — un {@code .properties} est lu en <strong>ISO-8859-1</strong> : écrits
 * en UTF-8, les accents arrivaient à l'écran en « contrÃ´le », « fÃ©vrier », « RÃ¨gles ». Les tests
 * d'intégration ne le voyaient pas, ils remplacent les libellés par « Manuel de test ». Ce test lit
 * le fichier comme le fait Spring ({@link Properties#load(InputStream)} : ISO-8859-1 et échappements Unicode)
 * et exige les libellés exacts.</p>
 */
class AssistantIaProprietesTest {

    @Test
    @DisplayName("Les libellés du corpus se lisent avec leurs accents (échappements \\u, pas d'UTF-8 brut)")
    void libellesDuCorpusAccentues() throws IOException {
        Properties p = new Properties();
        // Le fichier source, pas la ressource du classpath : celle des tests (src/test/resources) le masque.
        try (InputStream in = Files.newInputStream(Path.of("src/main/resources/application.properties"))) {
            p.load(in);
        }

        assertThat(p.getProperty("app.ia.corpus[0].libelle")).isEqualTo("Manuel de contrôle a priori (CNM, février 2026)");
        assertThat(p.getProperty("app.ia.corpus[1].libelle")).isEqualTo("Règles de gestion de PRS");
        assertThat(p.getProperty("app.ia.actif")).isEqualTo("${APP_IA_ACTIF:false}");
    }
}
