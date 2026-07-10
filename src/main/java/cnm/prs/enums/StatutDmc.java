package cnm.prs.enums;

/**
 * Cycle de vie d'un dossier de mise en concurrence (DMC).
 * <ul>
 *   <li>{@code A_PREPARER} : DMC créé, non encore engagé ; son type reste re-dérivable si le mode change ;</li>
 *   <li>{@code ENGAGE} : DMC engagé ; le type n'est plus re-dérivé automatiquement.</li>
 * </ul>
 * Liste volontairement minimale (extensible).
 */
public enum StatutDmc {
    A_PREPARER,
    ENGAGE
}
