package cnm.prs.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ⚠️ 2026-10-04 (arbitrages du pilote après le lot 4, §B4.2) — {@code GET /api/admin/offres/conservation} : la durée fixée
 * ({@code annees}, {@code null} = sans limite, la liste est alors vide) et les procédures dont la conservation est échue.
 */
public record ConservationOffresDto(Integer annees, List<Echue> echues) {

    /** Une procédure échue : {@code echeance} = clôture de la séance + {@code annees} ; {@code offresAPurger} encore sur disque. */
    public record Echue(Long idDmc, String reference, String objet, String etatSeance, LocalDateTime closeLe, LocalDateTime echeance,
            long offresAPurger) {
    }

    /** {@code POST …/{idDmc}/purger} : le compte rendu. */
    public record Purge(Long idDmc, int offresPurgees, LocalDateTime purgeeLe) {
    }
}
