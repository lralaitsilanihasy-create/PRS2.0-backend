package cnm.prs.enums;

/**
 * ⚠️ <strong>Urgence d'une ligne de l'accueil « À faire »</strong> (demande front du 2026-09-14, §2 et §4).
 * <strong>L'ordre de déclaration est le premier critère de tri</strong> des tâches.
 */
public enum UrgenceTache {
    /** Étape chronométrée dont le reste est négatif. */
    EN_RETARD,
    /** Reste entre 0 et le seuil « bientôt » (max(2 h, ⌈35 % du standard⌉)), bornes comprises. */
    BIENTOT,
    /** Reste au-delà du seuil « bientôt ». */
    DANS_LES_DELAIS,
    /**
     * Geste sans étape chronométrée (lettres, retraits), <strong>ou</strong> étape chronométrée dont l'entrée est
     * inconnue ({@code delaiCourant} rend alors un écoulé nul et aucune échéance : juger l'urgence sur un délai
     * inventé serait mentir — dossier antérieur au chronométrage, par exemple).
     */
    SANS_DELAI,
    /** Hors circuit : brouillon de la PRMP ou de l'UGPM. */
    HORS_DELAI,
    /** La balle est chez la PRMP (statut suspensif). */
    EN_PAUSE,
    /** Dossier suivi par la PRMP ou l'UGPM pendant qu'il avance à la CNM. */
    SUIVI
}
