package cnm.prs.enums;

/**
 * ⚠️ Lot 6 (2026-09-17, demande front « espace d'administration » §B2) — état d'accès d'une personne
 * tel que l'<strong>annuaire</strong> le présente : quatre cas exhaustifs, y compris celui d'une
 * personne du référentiel qui n'a aucun compte.
 *
 * <p>Distinct de {@link StatutCompte}, qui décrit le cycle de vie d'une <em>inscription</em>
 * ({@code EN_ATTENTE}, {@code ACTIF}, {@code REFUSE}) sur la seule table {@code t_compte_auth}. Le
 * rapprochement est fait par {@code AnnuaireService} :</p>
 *
 * <ul>
 *   <li>aucun compte pour cette personne → {@link #SANS_COMPTE} ;</li>
 *   <li>{@code STATUT = EN_ATTENTE} → {@link #EN_ATTENTE} ;</li>
 *   <li>{@code ACTIF = true} → {@link #ACTIF} (c'est ce booléen que le login consulte) ;</li>
 *   <li>tout le reste → {@link #DESACTIVE} : compte suspendu par l'Administrateur
 *       ({@code CompteAuthService.desactiver} ne touche que {@code ACTIF}) ou inscription refusée.
 *       {@link StatutCompte} n'a pas de valeur {@code DESACTIVE} — c'est ici qu'elle est nommée,
 *       pour l'écran d'administration.</li>
 * </ul>
 */
public enum StatutCompteAnnuaire {

    /** Compte connectable. */
    ACTIF,

    /** Compte existant mais fermé : suspendu par l'Administrateur, ou inscription refusée. */
    DESACTIVE,

    /** Inscription déposée, pas encore instruite. */
    EN_ATTENTE,

    /** Personne présente au référentiel, sans aucun compte d'authentification. */
    SANS_COMPTE
}
