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
    UGPM,

    /** ⚠️ 2026-10-04 — candidat à la soumission en ligne ({@code t_compte_candidat}), connecté par son adresse électronique. */
    CANDIDAT,

    /** ⚠️ 2026-10-04 (lot 2a) — membre d'une commission d'appel d'offres ({@code t_compte_cao}), connecté par son adresse électronique. */
    MEMBRE_CAO
}
