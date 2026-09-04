package cnm.prs.enums;

/**
 * Sens d'un mouvement de navette du projet de PV (colonne {@code t_pv_navette.SENS}).
 *
 * <p>Valeurs reprises littéralement de {@code docs/regles-gestion.md}
 * (§3.2, §3.5).</p>
 *
 * <p>⚠️ <strong>Navette à deux niveaux</strong> (spec pilote du 2026-09-04) — les deux derniers sens
 * décrivent les mouvements ENTRE ÉTAGES, que les trois premiers ne savaient pas dire : ils ne
 * distinguaient pas un projet soumis au CC d'un projet transmis au Président, ni un retour au Membre
 * d'un retour au CC. Sans eux, l'historique de navette d'un dossier à deux niveaux serait illisible —
 * on y verrait des allers-retours sans savoir entre qui et qui.</p>
 */
public enum SensNavette {

    /** Soumission du projet par le Membre vers le Président / CC (§3.5). */
    SOUMISSION,

    /** Retour pour rectification par le Président / CC (§3.2) — le projet redescend au Membre. */
    RETOUR_RECTIF,

    /** Acceptation du projet par le Président / CC (§3.2). */
    ACCEPTATION,

    /**
     * ⚠️ 2026-09-04 — <strong>acceptation intermédiaire du CC</strong> sur une navette à deux niveaux :
     * le CC valide le projet et le transmet au Président. Ce n'est PAS une {@link #ACCEPTATION} : la
     * navette n'est pas close, aucun avis n'est arrêté, aucune part n'est signée. Le distinguer est ce
     * qui permet de dire, plus tard, que le CC avait donné son accord avant le Président.
     */
    TRANSMISSION_PRESIDENT,

    /**
     * ⚠️ 2026-09-04 — <strong>retour du Président AU CC</strong> sur une navette à deux niveaux. Ce
     * n'est pas un {@link #RETOUR_RECTIF} : le projet ne redescend pas chez le Membre. Le CC arbitre
     * ensuite — il le renvoie au Membre, ou le re-transmet après échange. Le Président ne saute jamais
     * l'étage du CC, à l'aller comme au retour.
     */
    RETOUR_CC
}
