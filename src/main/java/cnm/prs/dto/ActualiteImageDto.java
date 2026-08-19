package cnm.prs.dto;

/**
 * Métadonnées d'une image d'actualité (jamais le contenu binaire — servi par
 * {@code GET /api/actualites/{id}/images/{idImage}}).
 */
public record ActualiteImageDto(Integer idImage, String nomFichier, Long taille, Integer ordre) {
}
