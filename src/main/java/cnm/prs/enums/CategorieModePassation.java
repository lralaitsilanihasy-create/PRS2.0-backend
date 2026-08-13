package cnm.prs.enums;

/**
 * ⚠️ Règle ajoutée (2026-08-13) — <strong>catégorie d'un mode de passation</strong>
 * (colonne {@code tr_mode_passation.CATEGORIE}, champ {@code categorie} de {@code ModePassationDto}).
 *
 * <p>Classification issue du Code des marchés publics : l'appel d'offres ouvert est le mode de
 * passation <strong>de droit commun</strong> ({@link #NORMAL}) ; les autres modes (appel d'offres
 * restreint, gré à gré…) sont <strong>dérogatoires</strong> ({@link #DEROGATOIRE}) et soumis à
 * conditions.</p>
 *
 * <p><strong>Purement déclaratif</strong> (au même titre que {@code publiciteRequise}) : aucune règle
 * ne s'y adosse pour l'instant. {@code null} = non classé — l'Administrateur classe chaque mode via
 * l'écran référentiel ; les modes créés à la volée (import PPM) naissent non classés.</p>
 */
public enum CategorieModePassation {

    /** Mode de droit commun (appel d'offres ouvert). */
    NORMAL,

    /** Mode dérogatoire, soumis à conditions (appel d'offres restreint, gré à gré…). */
    DEROGATOIRE
}
