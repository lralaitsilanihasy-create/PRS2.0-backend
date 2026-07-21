package cnm.prs.enums;

import java.util.Locale;

import cnm.prs.exception.BadRequestException;

/**
 * ⚠️ Règle ajoutée (2026-07-21) — <strong>portée</strong> d'un point de contrôle (colonne
 * {@code tr_points_ctrl.PORTEE}), data-driven comme l'{@code ID_SOUS_TYPE}. Pilote l'examen séquentiel
 * par ligne de marché d'un PPM.
 *
 * <ul>
 *   <li>{@link #LIGNE} — le point s'évalue <strong>par ligne de marché</strong> : un {@code ExamenDetail}
 *       par (marché × point), {@code ID_DETAIL} renseigné ;</li>
 *   <li>{@link #DOSSIER} — le point est <strong>inter-lignes</strong> (ex. « fractionnement illicite » =
 *       découpage entre marchés) : un seul {@code ExamenDetail} pour le dossier, {@code ID_DETAIL = null}.</li>
 * </ul>
 *
 * <p><strong>Défaut</strong> {@link #LIGNE} — jamais {@code null} côté API.</p>
 */
public enum PorteePointCtrl {

    /** Évalué par ligne de marché (un résultat par marché). */
    LIGNE,

    /** Évalué une fois au niveau dossier (résultat inter-lignes, idDetail = null). */
    DOSSIER;

    /**
     * Code API → enum : absent/vide → défaut {@link #LIGNE} (jamais null) ; code inconnu → 400 ciblé.
     */
    public static PorteePointCtrl depuisCodeOuDefaut(String code) {
        if (code == null || code.isBlank()) {
            return LIGNE;
        }
        try {
            return valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Portée de point de contrôle inconnue : « " + code
                    + " » — valeurs acceptées : LIGNE, DOSSIER.");
        }
    }
}
