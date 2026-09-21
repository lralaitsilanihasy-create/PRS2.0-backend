package cnm.prs.enums;

/**
 * Statut d'un intérim désigné — <strong>dérivé à la date du jour, jamais stocké ni reçu</strong>
 * (demande front du 2026-09-21, §B1 ; cf. {@code InterimService#statutEffectif}).
 *
 * <p>{@link #REVOQUE} prime dès que la date de révocation est atteinte ; sinon la période décide :
 * avant {@code dateDebut} → {@link #A_VENIR}, pendant → {@link #ACTIF}, après {@code dateFin} →
 * {@link #ACHEVE}. Contrairement à {@link StatutMandat}, aucune colonne-cache : un intérim ne périme pas
 * en base, il périme dans le temps.</p>
 */
public enum StatutInterim {

    /** Désigné, pas encore effectif ({@code dateDebut > aujourd'hui}). N'ouvre aucun droit. */
    A_VENIR,

    /** En cours : {@code dateDebut ≤ aujourd'hui ≤ dateFin} (ou sans fin), non révoqué. Seul statut qui ouvre les droits. */
    ACTIF,

    /** Terme atteint ({@code aujourd'hui > dateFin}) sans révocation. */
    ACHEVE,

    /** Clos avant terme par un acte explicite, à effet dès {@code dateRevocation}. Prime sur les dates. */
    REVOQUE
}
