package cnm.prs.dto;

import java.util.List;

/** Bilan d'une suppression en lot : identifiants effectivement supprimés vs. introuvables (tolérant). */
public record SuppressionLotResult(

        List<String> supprimes,

        List<String> introuvables) {
}
