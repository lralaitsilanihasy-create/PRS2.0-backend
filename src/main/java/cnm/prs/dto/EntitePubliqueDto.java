package cnm.prs.dto;

/**
 * Vue <strong>réduite et publique</strong> d'une entité contractante, exposée à l'écran
 * d'inscription PRMP (route publique {@code GET /api/auth/entites}). N'expose pas la hiérarchie
 * / l'organigramme : seulement de quoi choisir une entité.
 */
public record EntitePubliqueDto(
        Integer idEntiteContract,
        String libelleEntite,
        String adresse,
        String categorieEntite,
        String idLocalite,
        /** ⚠️ V48 (2026-09-26) — sigle de l'entité, {@code null} s'il n'est pas renseigné. */
        String sigle) {
}
