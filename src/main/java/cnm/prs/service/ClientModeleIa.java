package cnm.prs.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import cnm.prs.config.AssistantIaProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Client du serveur d'inférence de l'assistant IA, par son <strong>API compatible OpenAI</strong>
 * ({@code POST /chat/completions} en flux SSE) — Ollama en développement, vLLM ou Ollama en
 * production, sans une ligne à changer ici (ADR-0007).
 *
 * <p>Le mode « réflexion » des modèles qui en ont un est <strong>coupé</strong> : il allonge la
 * réponse sans rien apporter à une question documentaire. Deux réglages sont envoyés, chacun
 * compris par un serveur et ignoré par l'autre : {@code reasoning_effort: "none"} (Ollama) et
 * {@code chat_template_kwargs.enable_thinking: false} (vLLM). Si un serveur diffuse malgré tout un
 * raisonnement ({@code delta.reasoning}), il n'est jamais transmis à l'utilisateur.</p>
 */
@Component
public class ClientModeleIa {

    private static final Logger log = LoggerFactory.getLogger(ClientModeleIa.class);

    /** Température basse : une réponse documentaire doit être fidèle aux extraits, pas inventive. */
    private static final double TEMPERATURE = 0.2;

    /** Un message de la conversation envoyée au modèle ({@code system}, {@code user}). */
    public record Message(String role, String content) {
    }

    /** Le service de calcul est injoignable, a refusé la requête, ou n'a pas répondu à temps. */
    public static class ModeleIndisponibleException extends RuntimeException {
        public ModeleIndisponibleException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Fragment(List<Choix> choices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choix(Delta delta) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Delta(String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Modeles(List<Modele> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Modele(String id) {
    }

    private final AssistantIaProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public ClientModeleIa(AssistantIaProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * Génère une réponse en flux.
     *
     * @param messages   conversation (consigne système puis question avec ses extraits)
     * @param surFragment reçoit chaque morceau de texte dès qu'il arrive
     * @param annule     interrogé entre deux morceaux : {@code true} interrompt la génération (le
     *                   serveur d'inférence arrête de calculer quand la connexion se ferme)
     * @return le texte complet produit (partiel si interrompu)
     * @throws ModeleIndisponibleException service injoignable, réponse en erreur, ou délai dépassé
     */
    public String generer(List<Message> messages, Consumer<String> surFragment, BooleanSupplier annule) {
        return generer(messages, surFragment, annule, props.longueurMaxReponse());
    }

    /**
     * ⚠️ Même génération, avec un <strong>budget de sortie explicite</strong> (2026-09-20, pré-contrôle du
     * PPM, étape 6).
     *
     * <p>Pourquoi cette surcharge : {@code app.ia.longueur-max-reponse} (900) est calibré sur une
     * <strong>réponse de chat</strong>, lue par un humain. Une réponse <strong>structurée</strong> — un JSON
     * listant des pistes avec leurs constats — dépasse facilement ce budget, et le serveur d'inférence la
     * coupe alors <strong>au milieu</strong> : le JSON devient illisible, et l'analyse conclut « rien à
     * signaler » sur un plan qu'elle avait pourtant lu. Défaut trouvé en recette, invisible en test contre
     * un faux serveur, et qui n'apparaissait dans aucun journal avant qu'on y écrive l'extrait reçu.</p>
     */
    public String generer(List<Message> messages, Consumer<String> surFragment, BooleanSupplier annule,
            int maxJetons) {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("model", props.modele());
        corps.put("stream", true);
        corps.put("temperature", TEMPERATURE);
        corps.put("max_tokens", maxJetons > 0 ? maxJetons : props.longueurMaxReponse());
        corps.put("reasoning_effort", "none");
        corps.put("chat_template_kwargs", Map.of("enable_thinking", false));
        corps.put("messages", messages);

        HttpRequest requete = HttpRequest.newBuilder(URI.create(props.baseUrl() + "/chat/completions"))
                .timeout(Duration.ofSeconds(props.timeoutSecondes()))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(corps)))
                .build();

        long echeance = System.nanoTime() + Duration.ofSeconds(props.timeoutSecondes()).toNanos();
        StringBuilder reponse = new StringBuilder();
        HttpResponse<Stream<String>> resultat;
        try {
            resultat = http.send(requete, HttpResponse.BodyHandlers.ofLines());
        } catch (IOException e) {
            throw new ModeleIndisponibleException("Le service de calcul de l'assistant ne répond pas.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModeleIndisponibleException("La génération a été interrompue.", e);
        }
        try (Stream<String> lignes = resultat.body()) {
            if (resultat.statusCode() != 200) {
                String detail = lignes.limit(20).collect(Collectors.joining(" "));
                log.warn("Assistant IA : le serveur d'inférence a répondu {} — {}", resultat.statusCode(), detail);
                throw new ModeleIndisponibleException(
                        "Le service de calcul de l'assistant a refusé la demande (code " + resultat.statusCode() + ").", null);
            }
            var iterateur = lignes.iterator();
            while (iterateur.hasNext()) {
                if (annule.getAsBoolean()) {
                    break;
                }
                if (System.nanoTime() > echeance) {
                    throw new ModeleIndisponibleException("L'assistant a mis trop de temps à répondre.", null);
                }
                LigneFlux ligne = lire(iterateur.next());
                if (ligne.fin()) {
                    break;
                }
                if (ligne.texte() == null) {
                    continue;
                }
                reponse.append(ligne.texte());
                surFragment.accept(ligne.texte());
            }
        } catch (java.io.UncheckedIOException e) {
            throw new ModeleIndisponibleException("La connexion au service de calcul a été coupée.", e);
        }
        return reponse.toString();
    }

    /** Ce que porte une ligne du flux : un morceau de texte, la fin du flux, ou rien d'utile. */
    record LigneFlux(boolean fin, String texte) {
        static final LigneFlux RIEN = new LigneFlux(false, null);
        static final LigneFlux TERMINE = new LigneFlux(true, null);
    }

    /**
     * Lit une ligne SSE du serveur d'inférence : {@code data: {...}} porte un morceau de texte,
     * {@code data: [DONE]} clôt le flux. Un raisonnement éventuel ({@code delta.reasoning}) n'est pas
     * lu — le record {@link Delta} ne connaît que {@code content}.
     */
    LigneFlux lire(String ligne) {
        if (ligne == null || !ligne.startsWith("data:")) {
            return LigneFlux.RIEN;
        }
        String donnees = ligne.substring("data:".length()).strip();
        if (donnees.equals("[DONE]")) {
            return LigneFlux.TERMINE;
        }
        try {
            Fragment f = mapper.readValue(donnees, Fragment.class);
            if (f.choices() == null || f.choices().isEmpty() || f.choices().get(0).delta() == null) {
                return LigneFlux.RIEN;
            }
            String texte = f.choices().get(0).delta().content();
            return texte == null || texte.isEmpty() ? LigneFlux.RIEN : new LigneFlux(false, texte);
        } catch (JacksonException e) {
            log.debug("Assistant IA : ligne de flux illisible ignorée : {}", donnees);
            return LigneFlux.RIEN;
        }
    }

    /**
     * Le serveur d'inférence répond-il et connaît-il le modèle configuré ? Sonde courte
     * ({@code GET /models}, deux secondes) : sert l'état affiché par le front, jamais une décision.
     */
    public boolean disponible() {
        HttpRequest requete = HttpRequest.newBuilder(URI.create(props.baseUrl() + "/models"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();
        try {
            HttpResponse<String> r = http.send(requete, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) {
                return false;
            }
            Modeles m = mapper.readValue(r.body(), Modeles.class);
            return m.data() != null && m.data().stream().anyMatch(x -> props.modele().equals(x.id()));
        } catch (IOException | JacksonException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
