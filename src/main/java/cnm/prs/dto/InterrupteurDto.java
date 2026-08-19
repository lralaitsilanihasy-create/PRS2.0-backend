package cnm.prs.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Corps/réponse d'un interrupteur booléen de paramètre système
 * (ex. {@code /api/parametres/actualites-actives}).
 */
public record InterrupteurDto(@NotNull Boolean actif) {
}
