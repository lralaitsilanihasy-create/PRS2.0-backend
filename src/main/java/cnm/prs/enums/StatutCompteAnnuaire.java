package cnm.prs.enums;

/**
 * ⚠️ Lot 6 (2026-09-17, demande front « espace d'administration » §B2) — état d'accès d'une personne
 * tel que l'<strong>annuaire</strong> le présente : cinq cas exhaustifs, y compris celui d'une
 * personne du référentiel qui n'a aucun compte.
 *
 * <p>Distinct de {@link StatutCompte}, qui décrit le cycle de vie d'une <em>inscription</em>
 * ({@code EN_ATTENTE}, {@code ACTIF}, {@code REFUSE}) sur la seule table {@code t_compte_auth}. Le
 * rapprochement est fait par {@code AnnuaireService.statutDe}, dans cet ordre :</p>
 *
 * <ul>
 *   <li>aucun compte pour cette personne → {@link #SANS_COMPTE} ;</li>
 *   <li>{@code ACTIF = true} → {@link #ACTIF} — ce booléen prime, car c'est le seul que le login
 *       consulte : quelqu'un qui peut se connecter est actif, quoi que dise {@code STATUT} ;</li>
 *   <li>{@code STATUT = EN_ATTENTE} → {@link #EN_ATTENTE} ;</li>
 *   <li>{@code STATUT = REFUSE} → {@link #REFUSE} ;</li>
 *   <li>le reste ({@code STATUT = ACTIF}, ou nul sur une ligne héritée, avec {@code ACTIF = false})
 *       → {@link #SUSPENDU}.</li>
 * </ul>
 *
 * <p><strong>⚠️ Pourquoi {@link #SUSPENDU} et {@link #REFUSE} sont deux valeurs et non une.</strong>
 * Elles décrivent deux histoires sans rapport — un compte qu'on a fermé, et une inscription qu'on n'a
 * jamais ouverte — et la tuile « comptes suspendus » de l'accueil est une mesure de
 * <strong>sécurité</strong> : y verser les inscriptions refusées la gonflerait et la rendrait fausse.
 * Les deux restent distinguables en base bien que {@link StatutCompte} n'ait pas de valeur
 * {@code DESACTIVE}, parce que {@code CompteAuthService.desactiver} ne touche que le booléen
 * {@code ACTIF} en laissant {@code STATUT} à {@code ACTIF}, là où un refus écrit
 * {@code STATUT = REFUSE} et son {@code MOTIF_REFUS}.</p>
 */
public enum StatutCompteAnnuaire {

    /** Compte connectable. */
    ACTIF,

    /** Compte validé puis <strong>fermé</strong> par l'Administrateur ({@code ACTIF = false}, {@code STATUT} inchangé). */
    SUSPENDU,

    /** Inscription <strong>refusée</strong> ({@code STATUT = REFUSE}) : le compte n'a jamais été ouvert. */
    REFUSE,

    /** Inscription déposée, pas encore instruite. */
    EN_ATTENTE,

    /** Personne présente au référentiel, sans aucun compte d'authentification. */
    SANS_COMPTE
}
