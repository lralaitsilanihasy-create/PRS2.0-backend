package cnm.prs.dto;

import java.util.Map;

import jakarta.validation.constraints.NotNull;

/**
 * Corps de {@code PUT /api/fiches-marche/{idDmc}/blocs/{bloc}} — les valeurs du bloc, par code de champ
 * ({@code { "B05-GS-02": 8400000, "B05-GS-03": "BANCAIRE" }}). Le corps <strong>remplace</strong> les valeurs de
 * saisie du bloc : un code absent est effacé, une valeur vide aussi.
 */
public record BlocValeursRequest(@NotNull Map<String, Object> valeurs) {
}
