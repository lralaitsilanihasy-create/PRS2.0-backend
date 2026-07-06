package cnm.prs.enums;

/**
 * Population à laquelle appartient un compte d'authentification.
 */
public enum TypeActeur {

    /** Contrôleur CNM ({@code tr_controleur}). */
    CONTROLEUR,

    /** Personne Responsable des Marchés Publics, acteur externe ({@code t_prmp}). */
    PRMP,

    /** Unité de Gestion de la Passation des Marchés, rattachée à une PRMP de tutelle ({@code t_ugpm}). */
    UGPM
}
