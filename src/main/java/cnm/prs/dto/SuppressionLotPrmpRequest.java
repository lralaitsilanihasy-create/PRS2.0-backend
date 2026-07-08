package cnm.prs.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

/** Suppression en lot de PRMP par matricule (idPrmp). Au moins un matricule requis. */
public record SuppressionLotPrmpRequest(

        @NotEmpty
        List<String> matricules) {
}
