package cnm.prs.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Delai standard d'une etape — referentiel administrable, chronometrage des delais 2026-09-01.
 *
 * @param etape      valeur de {@code EtapeCircuit} (cle)
 * @param delaiHeures delai en HEURES ouvrees, au moins 1 (8 h = 1 jour ouvre)
 * @param libelle    libelle d'affichage
 */
public record DelaiStandardDto(
        String etape,
        @NotNull(message = "Le délai est obligatoire.")
        @Min(value = 1, message = "Le délai standard doit valoir au moins 1 heure ouvrée.")
        Integer delaiHeures,
        @Size(max = 100) String libelle) {
}
