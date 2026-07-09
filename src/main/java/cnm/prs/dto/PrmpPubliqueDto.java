package cnm.prs.dto;

/**
 * Vue <strong>réduite et publique</strong> d'une PRMP, exposée à l'écran d'inscription UGPM
 * (route publique {@code GET /api/auth/prmps}) pour le menu « PRMP de tutelle ». Miroir de
 * {@link EntitePubliqueDto} : seulement de quoi choisir une tutelle, sans données sensibles.
 */
public record PrmpPubliqueDto(
        String idPrmp,
        String nomPrmp,
        String prenomsPrmp) {
}
