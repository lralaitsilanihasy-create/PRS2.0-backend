package cnm.prs.dto;

import java.time.LocalDateTime;

/**
 * ⚠️ R4 (manuel révisé pour l'application, lot 1, §B5 ; V104, 2026-10-13) — une ligne de
 * {@code GET /api/dossiers/{id}/instantanes} : un écran figé à une soumission.
 *
 * @param numeroSoumission 1 à la soumission, +1 à chaque resoumission
 * @param ecran            {@code SEANCE}, {@code EVALUATION}, {@code AMI}, {@code ATTRIBUTION}, {@code CAO}, {@code AVIS}, {@code PLAN},
 *                         {@code ACTE}, {@code DSS}
 * @param empreinte        SHA-256 du JSON figé
 * @param taille           longueur du JSON figé, en caractères
 */
public record InstantaneDto(Long idInstantane, Integer numeroSoumission, String ecran, LocalDateTime figeLe, String figePar,
        String empreinte, int taille) {
}
