package cnm.prs.dto;

import java.util.List;

/**
 * Bilan d'une suppression en lot de contrôleurs (tolérant) : {@code supprimes} (sans activité métier, avec
 * données dérivées + compte), {@code introuvables} (matricules absents), {@code bloques} (avec activité métier —
 * subordonnés/examens/PV/vérifications/dispatchs/réceptions/demandes/lettres — non supprimés, comme le 409 unitaire).
 */
public record SuppressionLotControleurResult(

        List<String> supprimes,

        List<String> introuvables,

        List<String> bloques) {
}
