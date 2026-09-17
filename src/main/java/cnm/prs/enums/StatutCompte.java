package cnm.prs.enums;

/**
 * Cycle de vie d'un compte d'authentification (colonne {@code t_compte_auth.STATUT}).
 *
 * <p>Une inscription PRMP est créée {@link #EN_ATTENTE} ; l'Administrateur la passe à
 * {@link #ACTIF} (compte activable) ou {@link #REFUSE} (avec motif), depuis
 * {@code /api/inscriptions/{login}/valider|refuser} — seule porte de cette décision. Invariant
 * annoncé avec le booléen historique {@code ACTIF} : {@code ACTIF=true} ⟺ {@code STATUT=ACTIF}
 * (le login continue de s'appuyer sur {@code ACTIF}).</p>
 *
 * <p>⚠️ <strong>Une seule exception, volontaire</strong> (2026-09-17) :
 * {@code CompteAuthService.desactiver} pose {@code ACTIF = false} en laissant {@code STATUT} à
 * {@code ACTIF}. C'est ce qui distingue un compte <em>suspendu</em> d'une inscription
 * <em>refusée</em> (cf. {@link StatutCompteAnnuaire}). {@code activer}, lui, tient l'invariant et
 * refuse désormais (409) un compte {@link #EN_ATTENTE} ou {@link #REFUSE}.</p>
 */
public enum StatutCompte {

    /** Inscription déposée, en attente de vérification de l'arrêté par l'Administrateur. */
    EN_ATTENTE,

    /** Compte validé : la connexion est autorisée. */
    ACTIF,

    /** Inscription refusée par l'Administrateur (cf. {@code MOTIF_REFUS}). */
    REFUSE
}
