package cnm.prs.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Delai standard d'une etape — referentiel administrable, chronometrage des delais 2026-09-01.
 *
 * @param etape      valeur de {@code EtapeCircuit} (cle)
 * @param delaiJours delai en jours ouvres, au moins 1
 * @param libelle    libelle d'affichage
 */
public record DelaiStandardDto(
        String etape,
        @NotNull(message = "Le délai est obligatoire.")
        @Min(value = 1, message = "Le délai standard doit valoir au moins 1 jour ouvré.")
        Integer delaiJours,
        @Size(max = 100) String libelle) {
}
