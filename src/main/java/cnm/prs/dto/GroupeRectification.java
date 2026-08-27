package cnm.prs.dto;

/**
 * ⚠️ Audit 2026-08-27, lot B — groupe de validation des <strong>rectifications</strong>
 * ({@code PATCH /api/ppms/{id}/rectifier}, {@code PATCH /api/marches/{id}/rectifier}).
 *
 * <p>Ces deux endpoints étaient les seuls {@code @RequestBody} du dépôt sans validation : le corps
 * y arrive <strong>sans les champs d'identité figés</strong> ({@code idDossier}, {@code idPpm} —
 * conservés côté serveur), qu'un {@code @Valid} nu rendrait obligatoires. Le groupe permet de
 * valider le <strong>contenu</strong> (longueurs, bornes des montants, exercice) sans exiger
 * l'identité : les contraintes portant {@code groups = {Default.class, GroupeRectification.class}}
 * s'appliquent aux deux chemins, celles laissées au seul groupe par défaut (les {@code @NotNull} /
 * {@code @NotBlank} d'identité) restent réservées au {@code POST} et au {@code PUT}.</p>
 */
public interface GroupeRectification {
}
