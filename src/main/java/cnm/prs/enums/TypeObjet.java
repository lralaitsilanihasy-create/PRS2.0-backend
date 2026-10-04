package cnm.prs.enums;

/**
 * Type de l'objet métier référencé par une notification (colonne {@code t_notification.TYPE_OBJET}),
 * couplé à {@code ID_OBJET}. Permet de pointer l'élément concerné (dossier, PV, message) pour la
 * navigation depuis la notification.
 */
public enum TypeObjet {
    DOSSIER,
    PV,
    MESSAGE,
    /** ⚠️ 2026-10-04 (soumission en ligne, lot 2, §B6) — une procédure (DMC) : {@code idObjet} = {@code idDmc}. */
    PROCEDURE
}
