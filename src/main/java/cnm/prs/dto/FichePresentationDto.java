package cnm.prs.dto;

import java.util.List;

/**
 * ⚠️ 2026-10-17 (demande front « pieces-dmc-par-les-donnees », §B2) — la fiche de présentation d'un dossier né d'une fiche validée,
 * produite de ses données ({@code GET /api/dossiers/{id}/fiche-presentation}) : des sections de lignes « libellé : valeur ».
 * Les codes de section et de ligne sont stables ; une ligne sans valeur n'est pas servie.
 *
 * @param idDossier     le dossier présenté
 * @param idDmc         la fiche
 * @param versionFiche  la version validée présentée
 * @param sections      {@code IDENTIFICATION}, {@code MARCHE}, {@code PROCEDURE}, {@code COHERENCE}, {@code VALIDATION}
 */
public record FichePresentationDto(Integer idDossier, Long idDmc, Integer versionFiche, List<Section> sections) {

    public record Section(String code, String titre, List<Ligne> lignes) {
    }

    public record Ligne(String code, String libelle, String valeur) {
    }
}
