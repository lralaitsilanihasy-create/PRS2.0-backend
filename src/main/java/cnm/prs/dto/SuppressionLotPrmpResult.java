package cnm.prs.dto;

import java.util.List;

/**
 * Bilan d'une suppression en lot de PRMP (tolérant) : {@code supprimes} (existantes sans données liées, avec
 * leur compte), {@code introuvables} (matricules absents), {@code bloques} (existantes ayant des données liées —
 * dossiers/PPM/entités/demandes/indicateurs/UGPM — non supprimées, comme le 409 unitaire).
 */
public record SuppressionLotPrmpResult(

        List<String> supprimes,

        List<String> introuvables,

        List<String> bloques) {
}
