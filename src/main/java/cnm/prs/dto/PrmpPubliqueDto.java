package cnm.prs.dto;

/**
 * Vue <strong>réduite</strong> d'une PRMP (de quoi choisir une tutelle, sans données sensibles),
 * servie par {@code GET /api/auth/prmps} pour le menu « PRMP de tutelle » de l'inscription UGPM.
 *
 * <p>⚠️ Durcissement (2026-08-24) : la route n'est plus publique — elle exige le rôle
 * <strong>ADMINISTRATEUR</strong> (énumération de comptes, cf. {@code SecurityConfig}). Ce DTO
 * n'est plus le miroir « public » de {@link EntitePubliqueDto}, qui lui reste ouvert.</p>
 */
public record PrmpPubliqueDto(
        String idPrmp,
        String nomPrmp,
        String prenomsPrmp) {
}
