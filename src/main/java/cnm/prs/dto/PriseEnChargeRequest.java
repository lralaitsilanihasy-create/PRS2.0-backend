package cnm.prs.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Corps de la prise en charge d'une etape — chronometrage des delais, 2026-09-01.
 *
 * @param previsionJours prevision du porteur, en jours ouvres (au moins 1 : une tache qui ne prend
 *                       aucun jour n'a pas besoin d'etre prevue)
 */
public record PriseEnChargeRequest(
        @NotNull(message = "La prévision est obligatoire.")
        @Min(value = 1, message = "La prévision doit valoir au moins 1 jour ouvré.")
        Integer previsionJours) {
}
