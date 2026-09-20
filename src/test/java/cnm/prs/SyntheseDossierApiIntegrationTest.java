package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import cnm.prs.entity.ActionDossier;
import cnm.prs.entity.AuditLog;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Ppm;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;
import cnm.prs.repository.ActionDossierRepository;
import cnm.prs.repository.AuditLogRepository;

/**
 * ⚠️ Assistant IA, lot 2 (2026-09-20, étape 3) — <strong>l'API de la synthèse</strong>, de bout en bout,
 * contre un faux serveur d'inférence.
 *
 * <p>Ce que ces tests protègent, et qui est l'essentiel de l'étape :</p>
 * <ul>
 *   <li>l'<strong>ordre</strong> — les faits partent avant la moindre seconde de calcul, et un refus
 *       d'accès arrive <strong>avant l'ouverture du flux</strong>, en 403 franc plutôt qu'en message
 *       d'erreur noyé dans un flux déjà commencé ;</li>
 *   <li>le <strong>périmètre</strong> — l'endpoint n'a aucune garde de rôle, et n'en a pas besoin : la
 *       liste blanche s'en charge sous l'identité de l'appelant ;</li>
 *   <li>la <strong>trace</strong> — le journal d'audit dit quelles lectures ont abouti et lesquelles ont
 *       été écartées, ce qui est la preuve, a posteriori, que l'assistant n'a lu que le permis.</li>
 * </ul>
 */
class SyntheseDossierApiIntegrationTest extends CnmIntegrationTestSupport {

    private static final int DOSSIER_ANT = 840;
    private static final int PPM_ANT = 840;
    private static final String MODELE = "modele-test";
    private static final HttpServer SERVEUR;

    static {
        try {
            SERVEUR = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            SERVEUR.createContext("/v1/models", ex -> repondre(ex, 200, "application/json",
                    "{\"data\":[{\"id\":\"" + MODELE + "\"}]}"));
            SERVEUR.createContext("/v1/chat/completions", SyntheseDossierApiIntegrationTest::completer);
            SERVEUR.start();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void proprietesAssistant(DynamicPropertyRegistry registry) {
        registry.add("app.ia.actif", () -> "true");
        registry.add("app.ia.base-url", () -> "http://127.0.0.1:" + SERVEUR.getAddress().getPort() + "/v1");
        registry.add("app.ia.modele", () -> MODELE);
        registry.add("app.ia.timeout-secondes", () -> "20");
    }

    private static void completer(HttpExchange ex) throws IOException {
        ex.getRequestBody().readAllBytes();
        ex.getResponseHeaders().add("Content-Type", "text/event-stream");
        ex.sendResponseHeaders(200, 0);
        try (OutputStream out = ex.getResponseBody()) {
            for (String ligne : List.of(
                    "{\"choices\":[{\"delta\":{\"content\":\"**Où en est ce dossier**\\nLe dossier est \"}}]}",
                    "{\"choices\":[{\"delta\":{\"content\":\"soumis.\"}}]}",
                    "[DONE]")) {
                out.write(("data: " + ligne + "\n\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        }
    }

    private static void repondre(HttpExchange ex, int code, String type, String corps) throws IOException {
        byte[] octets = corps.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", type);
        ex.sendResponseHeaders(code, octets.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(octets);
        }
    }

    @Autowired private ActionDossierRepository actionRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PlatformTransactionManager transactions;

    /**
     * ⚠️ Les lignes d'audit sont écrites dans une transaction à part (c'est leur raison d'être : une
     * trace survit à l'échec de ce qu'elle trace). Elles échappent donc au rollback du test et
     * pollueraient le suivant — piège déjà rencontré au lot 1. On les purge, à part elles aussi.
     */
    @AfterEach
    void purgerLeJournalDeLAssistant() {
        TransactionTemplate t = new TransactionTemplate(transactions);
        t.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        t.executeWithoutResult(s -> jdbcTemplate.update(
                "DELETE FROM t_audit_log WHERE \"NOM_TABLE\" = 'assistant_ia'"));
    }

    private String tokenPrmp;
    private String tokenMembreAnt;
    private String tokenMembreToa;

    @BeforeEach
    void unDossierEtDeuxCommissions() {
        localiteRepository.save(localite("TOA", "Toamasina"));

        Dossier d = dossier(DOSSIER_ANT, "SOUMIS");
        d.setIdTypeDossier("DDP");
        d.setIdSousType("PPM");
        d.setIdPrmp("PRMP001");
        d.setIdLocalite("ANT");
        d.setDateSoumission(LocalDateTime.of(2026, 6, 2, 10, 30));
        dossierRepository.save(d);

        Ppm p = ppm(PPM_ANT, DOSSIER_ANT, "PRMP001");
        p.setReference("00840/MTP/PPM/2026");
        p.setIdLocalite("ANT");
        p.setDateSignature(LocalDate.of(2026, 1, 10));
        ppmRepository.save(p);

        ActionDossier a = new ActionDossier();
        a.setIdDossier(DOSSIER_ANT);
        a.setTypeAction("SOUMISSION");
        a.setDateAction(LocalDateTime.of(2026, 6, 2, 10, 30));
        a.setNomOperateur("Rasoanaivo Hery Fanomezana");
        actionRepository.save(a);

        tokenPrmp = bearer("PRMP001", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP001", null);
        tokenMembreAnt = bearer("MEMANT1", ProfilUtilisateur.MEMBRE, TypeActeur.CONTROLEUR, "MEMANT1", "ANT");
        tokenMembreToa = bearer("MEMTOA1", ProfilUtilisateur.MEMBRE, TypeActeur.CONTROLEUR, "MEMTOA1", "TOA");

        entityManager.flush();
        entityManager.clear();
    }

    // ------------------------------------------------------------------ 1. le périmètre

    @Test
    @DisplayName("⚠️ Un contrôleur d'une autre commission reçoit 403 AVANT l'ouverture du flux : le refus "
            + "est une erreur HTTP franche, pas un message noyé dans un flux déjà commencé")
    void autreCommission_403SansFlux() throws Exception {
        mvc.perform(post("/api/assistant-ia/dossiers/" + DOSSIER_ANT + "/synthese")
                        .header("Authorization", tokenMembreToa)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Sans session, rien : l'assistant n'est pas une porte dérobée")
    void sansSession_401() throws Exception {
        mvc.perform(post("/api/assistant-ia/dossiers/" + DOSSIER_ANT + "/synthese"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un dossier inexistant répond 404, sans rien faire travailler")
    void dossierInexistant_404() throws Exception {
        mvc.perform(post("/api/assistant-ia/dossiers/999999/synthese")
                        .header("Authorization", tokenMembreAnt)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ 2. le flux

    @Test
    @DisplayName("Le flux émet les FAITS avant le texte, puis la fin avec la mention : ce que le serveur "
            + "a lu s'affiche tout de suite, la prose arrive ensuite")
    void flux_faitsPuisTextePuisFin() throws Exception {
        String flux = synthetiser(tokenMembreAnt, DOSSIER_ANT);

        assertThat(flux).contains("event:faits", "event:texte", "event:fin");
        assertThat(flux.indexOf("event:faits")).isLessThan(flux.indexOf("event:texte"));
        assertThat(flux.indexOf("event:texte")).isLessThan(flux.indexOf("event:fin"));
        // Le texte arrive en MORCEAUX, chacun dans son événement : la phrase n'est entière qu'une fois
        // recollée par le navigateur. C'est le propre d'un flux, et le test le dit plutôt que de l'oublier.
        assertThat(flux).contains("Le dossier est ", "soumis.", "00840/MTP/PPM/2026");
        assertThat(flux).contains("Elle ne vaut pas avis");
    }

    @Test
    @DisplayName("⚠️ Les faits servis à une PRMP ne portent pas le journal interne de la CNM, et les "
            + "lectures écartées sont NOMMÉES dans le flux — rien ne disparaît en silence")
    void faitsDUnePrmp_sansJournalInterne() throws Exception {
        String flux = synthetiser(tokenPrmp, DOSSIER_ANT);

        assertThat(flux).contains("\"outilsRefuses\"", "journal du circuit");
        assertThat(flux).doesNotContain("\"titre\":\"Journal du circuit\"");
        assertThat(flux).doesNotContain("SOUMISSION le 02/06/2026");
    }

    @Test
    @DisplayName("Le contrôleur, lui, obtient le journal du circuit dans ses faits")
    void faitsDUnControleur_avecJournal() throws Exception {
        String flux = synthetiser(tokenMembreAnt, DOSSIER_ANT);

        assertThat(flux).contains("Journal du circuit", "SOUMISSION le 02/06/2026");
    }

    // ------------------------------------------------------------------ 3. la trace

    @Test
    @DisplayName("Chaque synthèse est journalisée avec les lectures ABOUTIES et les lectures ÉCARTÉES : "
            + "c'est la preuve, après coup, que l'assistant n'a lu que ce que le profil permettait")
    void journalDAudit_ditCeQuiAEteLuEtCeQuiAEteEcarte() throws Exception {
        synthetiser(tokenPrmp, DOSSIER_ANT);

        List<AuditLog> traces = auditLogRepository.findAll().stream()
                .filter(a -> "assistant_ia".equals(a.getNomTable()))
                .filter(a -> "SYNTHESE_DOSSIER".equals(a.getTypeAction()))
                .toList();
        assertThat(traces).hasSize(1);
        AuditLog trace = traces.get(0);
        assertThat(trace.getIdEnregistrement()).isEqualTo(String.valueOf(DOSSIER_ANT));
        assertThat(trace.getChampModifie()).isEqualTo(MODELE);
        assertThat(trace.getNouvelleValeur())
                .contains("Profil : PRMP")
                .contains("Lectures abouties : dossier")
                .contains("Lectures écartées : ")
                .contains("journal du circuit");
    }

    /** Demande la synthèse et rend le flux SSE complet, une fois la rédaction terminée. */
    private String synthetiser(String jeton, int idDossier) throws Exception {
        MvcResult resultat = mvc.perform(post("/api/assistant-ia/dossiers/" + idDossier + "/synthese")
                        .header("Authorization", jeton)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON))
                .andExpect(request().asyncStarted())
                .andReturn();
        resultat.getAsyncResult(20_000);
        return resultat.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
