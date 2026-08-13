package cnm.prs.enums;

/**
 * Statuts d'un mandat de PRMP (colonne {@code t_mandat.STATUT}).
 *
 * <p>Sauf {@link #ABROGE} — qui résulte d'un acte explicite (arrêté d'abrogation) et prime sur
 * tout le reste — le statut se <strong>déduit des dates</strong> à la date d'observation
 * (cf. {@code MandatService#statutEffectif}). Il est recalculé et persisté à chaque écriture,
 * mais c'est toujours la valeur dérivée qui est exposée par l'API : un mandat ne « périme » pas
 * tout seul en base, il périme dans le temps.</p>
 */
public enum StatutMandat {

    /** Mandat en cours : {@code dateDebut ≤ date ≤ dateFin} et non abrogé. Seul statut qui autorise le traitement. */
    ACTIF,

    /** Nomination prise mais pas encore effective ({@code dateDebut > date}) — n'autorise <strong>pas</strong> le traitement. */
    EN_TRANSITION,

    /** Mandat arrivé à son terme ({@code date > dateFin}) sans abrogation. */
    ACHEVE,

    /** Mandat interrompu avant terme par un acte explicite (abrogation). Prime sur les dates. */
    ABROGE
}
