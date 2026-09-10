package cnm.prs.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

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
 *       découpage entre marchés) : un seul {@code ExamenDetail} pour le dossier, {@code ID_DETAIL = null} ;</li>
 *   <li>{@link #FICHE} — ⚠️ 2026-09-02 — le point porte sur la <strong>fiche de présentation</strong>,
 *       document dérivé du plan ; évalué une fois, comme {@link #DOSSIER} ;</li>
 *   <li>{@link #AGPM} — ⚠️ 2026-09-02 — le point porte sur le <strong>projet d'AGPM</strong> ; évalué une
 *       fois, et servi au seul sous-type {@code PPM-AGPM}.</li>
 * </ul>
 *
 * <p><strong>Défaut</strong> {@link #LIGNE} — jamais {@code null} côté API.</p>
 *
 * <p><strong>⚠️ Ne testez jamais une portée par égalité pour décider du mode d'évaluation.</strong>
 * Utilisez {@link #parLigne()}. Jusqu'au 2026-09-02, deux gardes s'écrivaient {@code == DOSSIER} et
 * traitaient <em>tout le reste</em> comme du par-ligne : l'ajout de {@code FICHE} et {@code AGPM} les
 * aurait fait exiger une évaluation par marché, et accepter un {@code idDetail} sur un point qui n'en a
 * pas. Le prédicat range toute portée nouvelle du côté sûr — évaluée une fois — sans qu'on ait à y
 * penser.</p>
 */
public enum PorteePointCtrl {

    /** Évalué par ligne de marché (un résultat par marché). */
    LIGNE,

    /** Évalué une fois au niveau dossier (résultat inter-lignes, idDetail = null). */
    DOSSIER,

    /** ⚠️ 2026-09-02 — évalué une fois sur la fiche de présentation (idDetail = null). */
    FICHE,

    /** ⚠️ 2026-09-02 — évalué une fois sur le projet d'AGPM (idDetail = null), sous-type PPM-AGPM. */
    AGPM,

    /**
     * ⚠️ 2026-09-10 — <strong>constat de suppression</strong> : le résultat unique que l'examinateur pose
     * sur une ligne RETIRÉE par une mise à jour (RAS, ou observation). Évalué <strong>par ligne</strong>
     * comme { #LIGNE}, mais sur les seules lignes supprimées — et réciproquement, une ligne
     * supprimée ne doit rien d'autre.
     *
     * <p>Le résultat est stocké comme tout autre : un { t_examen_detail} dont l'{ ID_DETAIL}
     * est celui de la <strong>ligne retirée elle-même</strong>. Elle existe toujours dans la version —
     * la copie d'une mise à jour conserve les lignes supprimées, restaurables — donc rien n'est à
     * inventer pour l'y rattacher.</p>
     */
    SUPPRESSION;

    /**
     * Le point s'évalue-t-il <strong>marché par marché</strong> ({@code idDetail} renseigné) ? {@link #LIGNE}
     * et {@link #SUPPRESSION} le font ; toute autre portée s'évalue <strong>une seule fois</strong>, sans
     * ligne de marché.
     *
     * <p>⚠️ Ce prédicat dit <em>comment</em> un point se rattache, pas <em>à quelles lignes</em> : LIGNE
     * vise les lignes du plan, SUPPRESSION les seules lignes retirées. C'est la complétude qui tranche —
     * voir {@link #surLigneRetiree()}.</p>
     */
    public boolean parLigne() {
        return this == LIGNE || this == SUPPRESSION;
    }

    /**
     * ⚠️ 2026-09-10 — le point porte-t-il sur une ligne <strong>retirée</strong> par une mise à jour ?
     * Seule {@link #SUPPRESSION}. Les deux ensembles sont disjoints : une ligne du plan ne reçoit jamais
     * de constat de suppression, une ligne retirée ne reçoit rien d'autre.
     */
    public boolean surLigneRetiree() {
        return this == SUPPRESSION;
    }

    /** Liste des codes acceptés, pour les messages d'erreur — dérivée de l'énumération, jamais recopiée. */
    public static String codesAcceptes() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));
    }

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
                    + " » — valeurs acceptées : " + codesAcceptes() + ".");
        }
    }
}
