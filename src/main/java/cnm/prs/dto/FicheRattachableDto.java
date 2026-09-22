package cnm.prs.dto;

import java.time.LocalDateTime;

/**
 * ⚠️ Fiche marché (demande front du 2026-09-23, lot 1b, §B3) — une fiche que la PRMP peut rattacher à un dossier
 * {@code DAO} existant ({@code GET /api/fiches-marche/rattachables}) : dernière version validée, DMC encore sans
 * dossier. {@code version} et {@code dateValidation} sont ceux de cette version.
 */
public record FicheRattachableDto(Long idDmc, Integer idDetail, String refeDossierPpm, String designationMarche,
        Integer version, LocalDateTime dateValidation) {
}
