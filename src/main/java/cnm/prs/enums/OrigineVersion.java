package cnm.prs.enums;

/**
 * ⚠️ Versions archivées (demande pilote du 2026-09-06) — ce qui a produit une version archivée d'un
 * dossier ({@code t_version_dossier.ORIGINE}).
 *
 * <ul>
 *   <li>{@link #RECTIFICATION} : la version courante a été remplacée <em>en place</em> par une
 *       rectification (premier {@code PUT /api/saisies/ppm/{id}} d'un cycle) — la seule origine posée
 *       aujourd'hui.</li>
 *   <li>{@link #MISE_A_JOUR} : réservé. Les mises à jour de PPM conservent déjà chaque version sous la
 *       forme d'un dossier à part entière ({@code ID_DOSSIER_PARENT}) ; la valeur existe pour qu'une
 *       unification future n'exige pas de migration du contrat.</li>
 * </ul>
 */
public enum OrigineVersion {
    RECTIFICATION,
    MISE_A_JOUR
}
