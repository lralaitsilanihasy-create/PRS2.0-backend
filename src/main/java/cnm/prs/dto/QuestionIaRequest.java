package cnm.prs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Question posée à l'assistant IA ({@code POST /api/assistant-ia/questions}). Une question à la fois,
 * sans historique : le lot 1 répond sur le corpus documentaire, la conversation suivie viendra avec
 * le chatbot (lot 4 de {@code docs/plan-assistant-ia.md}).
 */
public record QuestionIaRequest(
        @NotBlank(message = "La question est obligatoire.")
        @Size(max = 1000, message = "La question ne doit pas dépasser 1 000 caractères.")
        String question) {
}
