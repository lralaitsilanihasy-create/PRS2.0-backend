package cnm.prs.dto;

import java.time.LocalDateTime;

/**
 * ⚠️ 2026-10-08 (lot 3 PI, tranche PI-a, §B1) — une invitation à la consultation restreinte, telle que le candidat invité la lit :
 * la procédure, son rang sur la liste, la date de l'invitation, l'état et la date limite de la procédure, la lettre.
 */
public record InvitationDto(Long idDmc, String reference, String objet, String autoriteContractante, Integer rang, String source,
        LocalDateTime inviteLe, String etatProcedure, String dateLimite, boolean lettreDisponible) {
}
