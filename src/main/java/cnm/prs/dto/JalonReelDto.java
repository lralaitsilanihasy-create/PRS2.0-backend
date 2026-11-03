package cnm.prs.dto;

import java.time.LocalDate;

/**
 * ⚠️ 2026-11-03 (demande front « jalons-reels », B1) — la date réelle d'un jalon d'une ligne du plan, déduite des faits enregistrés.
 *
 * @param idDetail   la ligne du plan demandé
 * @param idCapm     le processus CAPM ({@code t_capm})
 * @param dateReelle la date du fait (la première atteinte, pour une ligne à plusieurs lots)
 * @param source     ce qui donne la date, en clair (« Clôture du dossier … », « Première impression de l'avis »…)
 */
public record JalonReelDto(Integer idDetail, Integer idCapm, LocalDate dateReelle, String source) {
}
