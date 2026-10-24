package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import cnm.prs.config.HorlogeRecette;

/**
 * ⚠️ 2026-10-24 (demande front « horloge-recette », R1 ; ADR-0016) — l'horloge de recette, réglée à chaud par l'Administrateur.
 * Contexte distinct : la route et le bean ne montent qu'avec {@code app.recette.horloge.active=true}. Le fichier du décalage est
 * un fichier de test, jamais celui de la recette du poste.
 */
@SpringBootTest(properties = { "app.recette.horloge.active=true",
        "app.recette.horloge.fichier=${java.io.tmpdir}/prs-horloge-recette-test.txt" })
class HorlogeRecetteIntegrationTest extends CnmIntegrationTestSupport {

    private static final String URL = "/api/admin/horloge-recette";
    private static final Path FICHIER = Path.of(System.getProperty("java.io.tmpdir"), "prs-horloge-recette-test.txt");

    @Autowired
    private Clock clock;

    @AfterEach
    void heureReelle() throws Exception {
        HorlogeRecette.regler(Duration.ZERO);
        Files.deleteIfExists(FICHIER);
    }

    @Test
    @DisplayName("PUT maintenant : le serveur lit l'heure voulue (bean et horloge statique), le décalage est écrit et tracé avec l'heure réelle")
    void reglerSurUneDate() throws Exception {
        mvc.perform(get(URL).header("Authorization", tokenAdmin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.decalee").value(false));

        LocalDateTime seance = LocalDateTime.now(Clock.systemDefaultZone()).plusDays(34).withHour(9).withMinute(30).truncatedTo(ChronoUnit.MINUTES);
        mvc.perform(put(URL).header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"maintenant\":\"" + seance + "\",\"motif\":\"séance d'ouverture\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decalee").value(true));

        // Le bean injecté et l'horloge statique disent la même heure : celle de la fiche.
        assertThat(LocalDateTime.now(clock)).isCloseTo(seance, within(1, ChronoUnit.MINUTES));
        assertThat(LocalDateTime.now(HorlogeRecette.HORLOGE)).isCloseTo(seance, within(1, ChronoUnit.MINUTES));
        assertThat(Duration.parse(Files.readString(FICHIER, StandardCharsets.UTF_8))).isEqualTo(HorlogeRecette.decalage());
        // « heure-serveur » (H1) : tout le monde, sans connexion, lit cette heure-là.
        String heure = mvc.perform(get("/api/horloge")).andExpect(status().isOk())
                .andExpect(jsonPath("$.decalee").value(true)).andReturn().getResponse().getContentAsString();
        assertThat(LocalDateTime.parse(com.jayway.jsonpath.JsonPath.read(heure, "$.maintenant")))
                .isCloseTo(seance, within(1, ChronoUnit.MINUTES));

        // Le journal garde la vérité : l'entrée est datée à l'heure de recette et porte l'heure réelle.
        var trace = jdbcTemplate.queryForMap("SELECT \"DATE_ACTION\", \"NOUVELLE_VALEUR\" FROM t_audit_log "
                + "WHERE \"TYPE_ACTION\" = 'HORLOGE_RECETTE' ORDER BY \"ID_LOG\" DESC LIMIT 1");
        assertThat(((java.sql.Timestamp) trace.get("DATE_ACTION")).toLocalDateTime()).isCloseTo(seance, within(1, ChronoUnit.MINUTES));
        assertThat((String) trace.get("NOUVELLE_VALEUR")).contains("séance d'ouverture").contains("[recette — heure réelle : ");
    }

    @Test
    @DisplayName("PUT avancer : la durée s'ajoute ; DELETE ramène à l'heure réelle")
    void avancerPuisRevenir() throws Exception {
        mvc.perform(put(URL).header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"avancer\":\"P3D\",\"motif\":\"dépôts\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.decalageMinutes").value(3 * 24 * 60));
        mvc.perform(put(URL).header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"avancer\":\"PT2H\",\"motif\":\"heure limite\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.decalage").value("PT74H"));

        mvc.perform(delete(URL).header("Authorization", tokenAdmin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.decalee").value(false));
        assertThat(HorlogeRecette.mention()).isNull();
        assertThat(LocalDateTime.now(clock)).isCloseTo(LocalDateTime.now(Clock.systemDefaultZone()), within(5, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("400 : les deux réglages ou aucun, durée illisible, motif absent ; 403 hors Administrateur")
    void refus() throws Exception {
        mvc.perform(put(URL).header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motif\":\"x\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MAINTENANT_OU_AVANCER"));
        mvc.perform(put(URL).header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"maintenant\":\"2026-11-27T09:30:00\",\"avancer\":\"PT1H\",\"motif\":\"x\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MAINTENANT_OU_AVANCER"));
        mvc.perform(put(URL).header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"avancer\":\"3 jours\",\"motif\":\"x\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUREE_INVALIDE"));
        mvc.perform(put(URL).header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"avancer\":\"PT1H\"}")).andExpect(status().isBadRequest());
        mvc.perform(get(URL).header("Authorization", tokenMembre)).andExpect(status().isForbidden());
        assertThat(HorlogeRecette.decalee()).isFalse();
    }
}
