package cnm.prs.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Réglages de l'assistant IA local ({@code app.ia.*} — {@code docs/plan-assistant-ia.md}, lot 1 ;
 * ADR-0007).
 *
 * <p>Le modèle ne tourne <strong>pas</strong> dans le JVM : il est servi par un serveur d'inférence
 * à l'API compatible OpenAI (Ollama en développement, vLLM ou Ollama en production). Le backend n'en
 * connaît que l'URL et le nom du modèle — changer de modèle ne recompile rien.</p>
 *
 * <p>{@code actif=false} par défaut : sans activation explicite, l'assistant est masqué côté front et
 * son API ne répond pas.</p>
 *
 * @param actif               l'assistant est proposé aux utilisateurs
 * @param baseUrl             racine de l'API compatible OpenAI (ex. {@code http://localhost:11434/v1})
 * @param modele              nom du modèle chez le serveur d'inférence
 * @param timeoutSecondes     durée maximale d'une réponse complète, chargement du modèle compris
 * @param extraits            nombre de passages du corpus fournis au modèle pour une question
 * @param longueurMaxReponse  longueur maximale d'une réponse, en jetons
 * @param corpus              documents lus au démarrage ; un chemin vide est ignoré
 */
@ConfigurationProperties(prefix = "app.ia")
public record AssistantIaProperties(
        boolean actif,
        String baseUrl,
        String modele,
        int timeoutSecondes,
        int extraits,
        int longueurMaxReponse,
        List<DocumentCorpus> corpus) {

    public AssistantIaProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank()
                ? "http://localhost:11434/v1" : baseUrl.strip().replaceAll("/+$", "");
        modele = modele == null || modele.isBlank() ? "qwen3.5:9b-q4_K_M" : modele.strip();
        timeoutSecondes = timeoutSecondes > 0 ? timeoutSecondes : 120;
        extraits = extraits > 0 ? extraits : 5;
        longueurMaxReponse = longueurMaxReponse > 0 ? longueurMaxReponse : 900;
        corpus = corpus == null ? List.of() : List.copyOf(corpus);
    }

    /**
     * Un document du corpus.
     *
     * @param libelle nom cité à l'utilisateur (ex. « Manuel de contrôle a priori (CNM, février 2026) »)
     * @param chemin  fichier {@code .pdf} (découpé par page) ou {@code .md} (découpé par titre)
     */
    public record DocumentCorpus(String libelle, String chemin) {
    }
}
