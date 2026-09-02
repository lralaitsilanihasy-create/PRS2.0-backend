package cnm.prs.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Corps de la prise en charge d'une etape — chronometrage des delais, 2026-09-01.
 *
 * <p>⚠️ L'unite est passee du JOUR a l'HEURE ouvree le 2026-09-02 (8 h = 1 jour ouvre). Un client resté
 * sur {@code previsionJours} n'est PAS silencieusement accepte : la propriete inconnue est ignoree,
 * {@code previsionHeures} manque, et la reponse est un 400 nommant le champ. Un refus clair vaut mieux
 * qu'une valeur prise pour ce qu'elle n'est pas — 5 « jours » lus comme 5 heures fausseraient la date
 * annoncee a la PRMP sans que personne le voie.</p>
 *
 * @param previsionHeures prevision du porteur, en HEURES ouvrees (au moins 1)
 */
public record PriseEnChargeRequest(
        @NotNull(message = "La prévision est obligatoire.")
        @Min(value = 1, message = "La prévision doit valoir au moins 1 heure ouvrée.")
        Integer previsionHeures) {
}
