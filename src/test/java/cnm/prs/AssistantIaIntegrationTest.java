package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
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

/**
 * Assistant IA local, lot 1 ({@code docs/plan-assistant-ia.md}) — de bout en bout, contre un
 * <strong>faux serveur d'inférence</strong> à l'API compatible OpenAI (celle d'Ollama et de vLLM) :
 * flux SSE {@code sources} → {@code texte} → {@code fin}, journal d'audit, ouverture aux profils,
 * pannes du service de calcul, et réponse fixe quand le corpus ne contient rien de pertinent.
 */
class AssistantIaIntegrationTest extends CnmIntegrationTestSupport {

    private static final String MODELE = "modele-test";
    private static final HttpServer SERVEUR;
    private static final Path CORPUS;
    private static final AtomicReference<String> DERNIERE_REQUETE = new AtomicReference<>();
    private static final AtomicInteger APPELS = new AtomicInteger();
    private static final AtomicBoolean EN_PANNE = new AtomicBoolean();

    static {
        try {
            CORPUS = Files.createTempFile("corpus-ia", ".md");
            Files.writeString(CORPUS, """
                    # Manuel de test

                    ## Délais de traitement

                    L'examen d'un dossier prend généralement 48 heures à compter de sa réception par \
                    l'examinateur ; le délai de traitement maximum est de cinq jours ouvrés.

                    ## Fractionnement

                    Aucun marché ne peut être fractionné illicitement ; le fractionnement s'apprécie par compte.
                    """);
            SERVEUR = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            SERVEUR.createContext("/v1/models", ex -> repondre(ex, 200, "application/json",
                    "{\"data\":[{\"id\":\"" + MODELE + "\"}]}"));
            SERVEUR.createContext("/v1/chat/completions", AssistantIaIntegrationTest::completer);
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
        registry.add("app.ia.corpus[0].libelle", () -> "Manuel de test");
        registry.add("app.ia.corpus[0].chemin", CORPUS::toString);
        registry.add("app.ia.corpus[1].libelle", () -> "");
        registry.add("app.ia.corpus[1].chemin", () -> "");
    }

    /** Faux serveur : un flux OpenAI en trois morceaux, dont un raisonnement qui ne doit jamais sortir. */
    private static void completer(HttpExchange ex) throws IOException {
        DERNIERE_REQUETE.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        APPELS.incrementAndGet();
        if (EN_PANNE.get()) {
            repondre(ex, 500, "text/plain", "boom");
            return;
        }
        ex.getResponseHeaders().add("Content-Type", "text/event-stream");
        ex.sendResponseHeaders(200, 0);
        try (OutputStream out = ex.getResponseBody()) {
            for (String ligne : List.of(
                    "{\"choices\":[{\"delta\":{\"role\":\"assistant\",\"content\":\"L'examen prend \"}}]}",
                    "{\"choices\":[{\"delta\":{\"reasoning\":\"RAISONNEMENT-INTERNE\"}}]}",
                    "{\"choices\":[{\"delta\":{\"content\":\"48 heures [1].\"}}]}",
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

    @Autowired
    private PlatformTransactionManager transactions;

    @BeforeEach
    void serveurEnEtat() {
        EN_PANNE.set(false);
    }

    /**
     * ⚠️ Les lignes de journal de l'assistant sont écrites par son pool de calcul, dans leur PROPRE
     * transaction : l'annulation de fin de test ne les touche pas. Laissées en base, elles faussaient
     * les tests qui comptent le journal (`AuditLogListeIntegrationTest`, constat du 2026-09-18). On les
     * purge donc dans une transaction à part, validée — un DELETE dans la transaction du test serait
     * annulé avec elle.
     */
    @AfterEach
    void purgerLeJournalDeLAssistant() {
        TransactionTemplate t = new TransactionTemplate(transactions);
        t.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        t.executeWithoutResult(s -> jdbcTemplate.update("DELETE FROM t_audit_log WHERE \"NOM_TABLE\" = 'assistant_ia'"));
    }

    // ------------------------------------------------------------------ état

    @Test
    @DisplayName("GET /etat — actif, service de calcul disponible, documents du corpus listés ; ouvert à la PRMP comme au Membre")
    void etat_actifEtOuvertAuxProfils() throws Exception {
        for (String jeton : List.of(tokenPrmp, tokenMembre, tokenAdmin)) {
            mvc.perform(get("/api/assistant-ia/etat").header("Authorization", jeton))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.actif").value(true))
                    .andExpect(jsonPath("$.disponible").value(true))
                    .andExpect(jsonPath("$.modele").value(MODELE))
                    .andExpect(jsonPath("$.documents", hasSize(1)))
                    .andExpect(jsonPath("$.documents[0].libelle").value("Manuel de test"))
                    .andExpect(jsonPath("$.documents[0].passages").value(2));
        }
    }

    @Test
    @DisplayName("Sans session : 401 sur l'état comme sur les questions")
    void sansSession_401() throws Exception {
        mvc.perform(get("/api/assistant-ia/etat")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/assistant-ia/questions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Quel délai ?\"}")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ questions

    @Test
    @DisplayName("POST /questions — flux sources → texte → fin ; l'extrait pertinent est fourni au modèle ; le raisonnement n'est jamais transmis")
    void question_fluxComplet() throws Exception {
        String question = "Quel est le délai de traitement d'un dossier ? " + UUID.randomUUID();

        String flux = poser(tokenMembre, question);

        assertThat(flux).containsSubsequence("event:sources", "event:texte", "event:texte", "event:fin");
        assertThat(flux).contains("\"reference\":\"Délais de traitement\"")
                .contains("L'examen prend ").contains("48 heures [1].")
                .doesNotContain("RAISONNEMENT-INTERNE")
                .doesNotContain("event:erreur");
        assertThat(DERNIERE_REQUETE.get())
                .contains("\"model\":\"" + MODELE + "\"")
                .contains("\"stream\":true")
                .contains("\"reasoning_effort\":\"none\"")
                .contains("[1] Manuel de test — Délais de traitement")
                .contains("cinq jours ouvrés")
                .contains(question)
                .contains("Membre de commission");
    }

    @Test
    @DisplayName("Journal d'audit : une ligne par échange (qui, modèle, question, réponse, sources), aucune ligne en double de l'intercepteur")
    void question_journalisee() throws Exception {
        String question = "Quel est le délai de traitement ? " + UUID.randomUUID();

        poser(tokenPrmp, question);

        List<Map<String, Object>> lignes = jdbcTemplate.queryForList(
                "SELECT \"IM_ACTEUR\", \"TYPE_ACTION\", \"CHAMP_MODIFIE\", \"NOUVELLE_VALEUR\" FROM t_audit_log "
                        + "WHERE \"NOM_TABLE\" = 'assistant_ia' AND \"NOUVELLE_VALEUR\" LIKE ?", "%" + question + "%");
        assertThat(lignes).hasSize(1);
        Map<String, Object> ligne = lignes.get(0);
        assertThat(ligne.get("IM_ACTEUR")).isEqualTo("PRMP001");
        assertThat(ligne.get("TYPE_ACTION")).isEqualTo("QUESTION");
        assertThat(ligne.get("CHAMP_MODIFIE")).isEqualTo(MODELE);
        assertThat((String) ligne.get("NOUVELLE_VALEUR"))
                .contains("Profil : PRMP")
                .contains("L'examen prend 48 heures [1].")
                .contains("[1] Manuel de test — Délais de traitement");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_audit_log WHERE \"NOM_TABLE\" = 'assistant-ia'", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Aucun passage pertinent : réponse fixe, le modèle n'est pas appelé — il ne répondra jamais de mémoire")
    void question_sansPassage_reponseFixeSansModele() throws Exception {
        int avant = APPELS.get();

        String flux = poser(tokenMembre, "xylophone zeppelin");

        assertThat(flux).contains("event:sources").contains("[]")
                .contains("aucun passage qui traite de cette question")
                .contains("event:fin");
        assertThat(APPELS.get()).isEqualTo(avant);
    }

    @Test
    @DisplayName("Service de calcul en panne : événement erreur lisible, échange journalisé en échec")
    void question_serveurEnPanne_erreurEtJournal() throws Exception {
        EN_PANNE.set(true);
        String question = "Comment apprécier le fractionnement ? " + UUID.randomUUID();

        String flux = poser(tokenMembre, question);

        assertThat(flux).contains("event:erreur").contains("a refusé la demande (code 500)")
                .doesNotContain("event:fin");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT \"TYPE_ACTION\" FROM t_audit_log WHERE \"NOM_TABLE\" = 'assistant_ia' AND \"NOUVELLE_VALEUR\" LIKE ?",
                String.class, "%" + question + "%")).isEqualTo("QUESTION_ECHEC");
    }

    @Test
    @DisplayName("Question vide ou trop longue : 400, rien n'est lancé")
    void question_invalide_400() throws Exception {
        mvc.perform(post("/api/assistant-ia/questions").header("Authorization", tokenMembre)
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/assistant-ia/questions").header("Authorization", tokenMembre)
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + "a".repeat(1001) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    /** Pose la question et rend le flux SSE complet, une fois la réponse terminée. */
    private String poser(String jeton, String question) throws Exception {
        MvcResult resultat = mvc.perform(post("/api/assistant-ia/questions").header("Authorization", jeton)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + question.replace("\"", "\\\"") + "\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        resultat.getAsyncResult(20_000);
        return resultat.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
