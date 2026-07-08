package cnm.prs.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

/** Suppression en lot de contrôleurs par matricule (imControleur). Au moins un matricule requis. */
public record SuppressionLotControleurRequest(

        @NotEmpty
        List<String> matricules) {
}
