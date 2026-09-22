package cnm.prs.dto;

import jakarta.validation.constraints.NotNull;

/** ⚠️ Fiche marché (2026-09-23, lot 1b, §B3) — corps de {@code PUT /api/dossiers/{id}/fiche-marche}. */
public record RattachementFicheRequest(@NotNull Long idDmc) {
}
