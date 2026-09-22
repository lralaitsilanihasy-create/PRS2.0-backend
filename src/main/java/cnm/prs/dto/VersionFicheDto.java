package cnm.prs.dto;

import java.time.LocalDateTime;

/**
 * Une version <strong>validée</strong> de la fiche marché ({@code GET /api/fiches-marche/{idDmc}/versions}) : de quoi
 * lister l'historique ; le détail se lit sur {@code /versions/{n}}.
 */
public record VersionFicheDto(Integer idFiche, Integer version, String statut, String typeMarche,
        LocalDateTime dateCreation, LocalDateTime dateValidation, String validePar, int nbValeurs) {
}
