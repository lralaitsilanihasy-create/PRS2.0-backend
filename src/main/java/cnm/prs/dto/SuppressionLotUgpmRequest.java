package cnm.prs.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

/** Suppression en lot d'UGPM par matricule (idUgpm). Au moins un matricule requis. */
public record SuppressionLotUgpmRequest(

        @NotEmpty
        List<String> matricules) {
}
