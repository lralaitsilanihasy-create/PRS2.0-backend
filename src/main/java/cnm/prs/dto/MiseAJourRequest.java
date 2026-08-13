package cnm.prs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps de {@code POST /api/saisies/ppm/{idDossier}/mise-a-jour}.
 *
 * @param motif motif métier de la mise à jour — <strong>obligatoire</strong> : c'est lui qui justifie la
 *              nouvelle version dans l'historique (400 s'il est vide)
 */
public record MiseAJourRequest(
        @NotBlank(message = "Le motif de la mise à jour est obligatoire.")
        @Size(max = 500)
        String motif) {
}
