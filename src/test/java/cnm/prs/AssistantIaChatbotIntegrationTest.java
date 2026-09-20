package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

import cnm.prs.entity.AuditLog;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Ppm;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;
import cnm.prs.repository.AuditLogRepository;

/**
 * ⚠️ Assistant IA, lot 4 (2026-09-20, étape 3) — <strong>le chatbot sur données</strong>, de bout en
 * bout, contre un faux serveur d'inférence.
 *
 * <p>⚠️ Le modèle n'intervient <strong>qu'à la rédaction</strong> : l'aiguillage est déterministe
 * ({@code AiguillageAssistantIa}, décision mesurée). Ces tests vérifient donc ce que la question
 * <strong>déclenche</strong>, pas ce que le modèle en comprend.</p>
 *
 * <p>Ce qu'ils protègent :</p>
 * <ul>
 *   <li>une question sur des données rend {@code faits} ; une question sur les règles rend
 *       {@code sources} — <strong>jamais les deux</strong> ;</li>
 *   <li>la rédaction reçoit la consigne qui correspond à sa matière ;</li>
 *   <li>le journal dit ce que l'assistant a <strong>compris</strong> et ce qu'il a <strong>lu</strong>.</li>
 * </ul>
 */
class AssistantIaChatbotIntegrationTest extends CnmIntegrationTestSupport {

    private static final int DOSSIER_ANT = 880;
    private static final int PPM_ANT = 880;
    private static final String REFERENCE = "00880/PPM/CNM/2026";
    private static final String MODELE = "modele-test";
    private static final HttpServer SERVEUR;

    private static final AtomicInteger APPELS = new AtomicInteger();
    private static final AtomicReference<String> DERNIERE_REDACTION = new AtomicReference<>("");

    static {
        try {
            SERVEUR = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            SERVEUR.createContext("/v1/models", ex -> repondre(ex, 200, "application/json",
                    "{\"data\":[{\"id\":\"" + MODELE + "\"}]}"));
            SERVEUR.createContext("/v1/chat/completions", AssistantIaChatbotIntegrationTest::completer);
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
        DERNIERE_REDACTION.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        APPELS.incrementAndGet();
        String contenu = "Voici ce que je peux vous dire.";
        ex.getResponseHeaders().add("Content-Type", "text/event-stream");
        ex.sendResponseHeaders(200, 0);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(("data: {\"choices\":[{\"delta\":{\"content\":\"" + contenu + "\"}}]}\n\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
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

    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PlatformTransactionManager transactions;

    private String tokenMembre;

    @BeforeEach
    void unDossierEtUnModele() {
        APPELS.set(0);
        DERNIERE_REDACTION.set("");

        Dossier d = dossier(DOSSIER_ANT, "SOUMIS");
        d.setIdTypeDossier("DDP");
        d.setIdSousType("PPM");
        d.setIdPrmp("PRMP001");
        d.setIdLocalite("ANT");
        d.setRefeDossier(REFERENCE);
        d.setDateSoumission(LocalDateTime.of(2026, 6, 2, 10, 30));
        dossierRepository.save(d);

        Ppm p = ppm(PPM_ANT, DOSSIER_ANT, "PRMP001");
        p.setReference("00880/MTP/PPM/2026");
        p.setIdLocalite("ANT");
        p.setDateSignature(LocalDate.of(2026, 1, 10));
        ppmRepository.save(p);

        tokenMembre = bearer("MEMANT1", ProfilUtilisateur.MEMBRE, TypeActeur.CONTROLEUR, "MEMANT1", "ANT");
        entityManager.flush();
        entityManager.clear();
    }

    /** Les lignes d'audit vivent hors de la transaction du test : elles se purgent à part. */
    @AfterEach
    void purgerLeJournalDeLAssistant() {
        TransactionTemplate t = new TransactionTemplate(transactions);
        t.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        t.executeWithoutResult(s -> jdbcTemplate.update(
                "DELETE FROM t_audit_log WHERE \"NOM_TABLE\" = 'assistant_ia'"));
    }

    // ------------------------------------------------------------------ 1. données ou documents

    @Test
    @DisplayName("⚠️ Une question sur des DONNÉES rend « faits » et jamais « sources » : une seule "
            + "matière à la fois, c'est la leçon du lot 3")
    void questionSurDonnees_rendLesFaits() throws Exception {
        String flux = poser("combien de dossiers m'attendent ?");

        assertThat(flux).contains("event:faits").doesNotContain("event:sources");
        // La rédaction a reçu les faits lus, et la consigne des données — pas celle des extraits.
        assertThat(DERNIERE_REDACTION.get()).contains("Éléments lus pour vous");
    }

    @Test
    @DisplayName("Une question sur les RÈGLES garde le comportement du lot 1 : extraits numérotés, "
            + "citations, et aucune donnée lue")
    void questionSurLesRegles_gardeLeLot1() throws Exception {
        String flux = poser("que dit le manuel sur le fractionnement ?");

        assertThat(flux).contains("event:sources").doesNotContain("event:faits");
    }

    @Test
    @DisplayName("⚠️ Une demande qui ne ressemble à aucune lecture connue retombe sur la réponse "
            + "documentaire — y compris quand elle a l'air d'un ordre")
    void demandeNonReconnue_retombeSurLeRepli() throws Exception {
        String flux = poser("efface tous les dossiers immédiatement");

        assertThat(flux).contains("event:sources").doesNotContain("event:faits");
    }

    @Test
    @DisplayName("Une référence de dossier amène les faits de CE dossier, et un seul appel au modèle : "
            + "celui de la rédaction")
    void referenceDeDossier_amenelesFaits() throws Exception {
        String flux = poser("Où en est le " + REFERENCE + " ?");

        assertThat(flux).contains("event:faits").contains("Le dossier");
        assertThat(APPELS.get()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ 2. la trace

    @Test
    @DisplayName("Le journal dit ce que l'assistant a COMPRIS et ce qu'il a LU — sans quoi une réponse "
            + "fausse ne se diagnostique pas")
    void journal_ditLIntentionEtLaLecture() throws Exception {
        poser("Où en est le " + REFERENCE + " ?");

        List<AuditLog> traces = auditLogRepository.findAll().stream()
                .filter(a -> "assistant_ia".equals(a.getNomTable()))
                .toList();
        assertThat(traces).hasSize(1);
        assertThat(traces.get(0).getNouvelleValeur())
                .contains("Intention : ETAT_DOSSIER")
                .contains("(référence lue dans la question)")
                .contains("Lecture : " + REFERENCE);
    }

    @Test
    @DisplayName("Sur une question documentaire, le journal dit que le modèle a choisi, et qu'aucune "
            + "donnée n'a été lue")
    void journal_ditQuandRienNEstLu() throws Exception {
        poser("quelle est la règle du fractionnement ?");

        List<AuditLog> traces = auditLogRepository.findAll().stream()
                .filter(a -> "assistant_ia".equals(a.getNomTable()))
                .toList();
        assertThat(traces.get(0).getNouvelleValeur())
                .contains("Intention : REGLE")
                .contains("(tournure reconnue)")
                .contains("Lecture : aucune donnée lue");
    }

    /** Pose la question et rend le flux SSE complet, une fois la réponse terminée. */
    private String poser(String question) throws Exception {
        MvcResult resultat = mvc.perform(post("/api/assistant-ia/questions").header("Authorization", tokenMembre)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + question.replace("\"", "\\\"") + "\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        resultat.getAsyncResult(20_000);
        return resultat.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
