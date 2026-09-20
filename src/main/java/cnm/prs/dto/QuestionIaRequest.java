package cnm.prs.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Question posée à l'assistant IA ({@code POST /api/assistant-ia/questions}).
 *
 * <p>⚠️ <strong>L'historique vient de l'écran</strong> (lot 4, 2026-09-20), et le serveur reste
 * <strong>sans mémoire</strong>. Deux raisons de ne pas le garder côté serveur : il faudrait le
 * rattacher à une session — donc <strong>stocker des questions</strong> qui peuvent porter des données
 * sensibles — et un historique long ne tient de toute façon pas dans la fenêtre d'un modèle local.</p>
 *
 * <p>Il est <strong>borné</strong> à trois tours, et chaque texte est coupé : au-delà, la question
 * courante se noierait dans ce qui précède. Il traverse le même <strong>désamorçage</strong> que les
 * textes de dossiers — c'est une donnée, jamais une consigne.</p>
 */
public record QuestionIaRequest(
        @NotBlank(message = "La question est obligatoire.")
        @Size(max = 1000, message = "La question ne doit pas dépasser 1 000 caractères.")
        String question,

        @Valid
        // ⚠️ La borne utile est celle du SERVEUR (trois tours, cf. AssistantIaService) : il ne garde que
        // les plus récents. Celle-ci est une simple garde contre l'excès — l'écran n'a pas à connaître la
        // taille de la fenêtre du modèle, et un client qui en envoie cinq reçoit une réponse, pas un 400.
        @Size(max = 20, message = "L'historique porte au plus vingt échanges.")
        List<TourIa> historique) {

    /** Les tours précédents, dans l'ordre : le plus ancien d'abord. Jamais {@code null}. */
    public List<TourIa> historique() {
        return historique == null ? List.of() : historique;
    }

    /** Un échange déjà eu : ce que l'utilisateur a demandé, ce que l'assistant a répondu. */
    public record TourIa(
            @Size(max = 1000, message = "Une question de l'historique ne doit pas dépasser 1 000 caractères.")
            String question,
            @Size(max = 2000, message = "Une réponse de l'historique ne doit pas dépasser 2 000 caractères.")
            String reponse) {
    }
}
