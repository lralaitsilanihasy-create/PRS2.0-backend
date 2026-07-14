package cnm.prs.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/**
 * Processus de marché saisi dans une ligne de marché ({@link SaisieMarcheLigne}) : un
 * {@code idCapm} (FK {@code t_capm}) avec ses dates prévisionnelles début/fin. Persisté en une
 * ligne {@code t_marche_prevision}. Les messages portent le nom de champ attendu par le contrat.
 */
public record ProcessusMarche(

        @NotNull(message = "Le processus est obligatoire.")
        Integer idCapm,

        @NotNull(message = "La date de début est obligatoire.")
        LocalDate dateDebut,

        // dateFin OPTIONNELLE : une prévision peut n'avoir qu'un début (fin non connue / ouverte).
        // La cohérence chronologique (dateDebut<dateFin, séquence) n'est vérifiée que si dateFin est présente.
        LocalDate dateFin) {
}
