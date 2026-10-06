package cnm.prs.dto;

import java.time.LocalDateTime;

/** ⚠️ 2026-10-06 (DAO complet, §B2) — {@code GET /api/fiches-marche/{idDmc}/specifications} : le fichier joint, sans son contenu. */
public record SpecificationsDto(String nomFichier, long taille, LocalDateTime deposeLe, String deposePar) {
}
