package cnm.prs.enums;

/**
 * ⚠️ <strong>À quel titre le connecté peut poser le geste</strong> d'une ligne de l'accueil « À faire » (demande
 * front du 2026-09-14, §2 ; arbitrage 1). Seules les lignes {@link #TITULAIRE} vont dans la liste principale, les
 * compteurs et le badge ; les autres forment le <strong>bloc délégation</strong>, replié.
 */
public enum ModeTache {
    /** Le geste lui revient en propre. */
    TITULAIRE,
    /** Réalisable par une paire active de {@code t_delegation_profil} (profil courant → profil porteur). */
    DELEGATION,
    /**
     * Par intérim : tâche du titulaire servie à son <strong>intérimaire désigné</strong> ({@code interimDe} et
     * {@code idInterim} renseignés sur la ligne ; 2026-09-21, §B4.6), ou — sens d'origine, {@code idInterim} nul —
     * visa par intérim ponctuel d'un P/CC autre que le dispatcheur (note d'intérim requise).
     */
    INTERIM,
    /** Dossier ciblé sur un collègue de même profil (rattachement), que la localité permet de traiter. */
    COLLEGUE,
    /** Pré-dispatch d'un dossier régional par le Président, à la place du CC (arbitrage 2). */
    SUPPLEANCE
}
