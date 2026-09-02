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
    AGPM;

    /**
     * Le point s'évalue-t-il <strong>marché par marché</strong> ? Seule {@link #LIGNE} le fait ; toute
     * autre portée s'évalue <strong>une seule fois</strong>, sans ligne de marché.
     */
    public boolean parLigne() {
        return this == LIGNE;
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
