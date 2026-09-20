package cnm.prs.enums;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3) — <strong>cycle de vie d'un signalement</strong>
 * ({@code t_anomalie.STATUT}).
 *
 * <p><strong>Rien ne s'efface</strong> (plan, §4, lot 3, 3.f, condition 3). Un signalement que la PRMP
 * fait disparaître en modifiant sa ligne n'est pas supprimé : il passe {@link #LEVE_MODIFICATION}, et le
 * contrôleur voit ce qui a changé. C'est la parade à l'évasion classique d'un détecteur de
 * fractionnement — reformuler ou réimputer les lignes jusqu'à ce que l'alarme se taise. Si le signalement
 * disparaissait sans trace, la dissuasion disparaîtrait avec lui.</p>
 */
public enum StatutSignalement {

    /** Détecté et non traité — c'est ce que la PRMP et le contrôleur ont à regarder. */
    OUVERT,

    /**
     * Écarté par la PRMP ou par un contrôleur, <strong>avec motif obligatoire</strong>. L'écartement
     * d'une PRMP est visible du contrôleur, et celui d'un contrôleur de sa hiérarchie (décisions du
     * pilote du 2026-09-18) : elle le sait au moment d'écarter, sans quoi il n'y aurait pas dissuasion
     * mais un piège.
     */
    ECARTE,

    /**
     * Ne ressort plus de la détection parce que le plan a changé. Conservé avec le détail de ce qui a
     * changé — jamais effacé.
     */
    LEVE_MODIFICATION
}
