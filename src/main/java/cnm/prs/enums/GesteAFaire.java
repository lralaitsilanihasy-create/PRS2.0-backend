package cnm.prs.enums;

/**
 * ⚠️ <strong>Gestes de l'accueil « À faire »</strong> (demande front du 2026-09-14, §3) — l'action proposée sur
 * une ligne. Le serveur ne sert que le code ; le libellé et l'écran qui porte le geste sont côté front.
 *
 * <p>Un geste n'est proposé que si la garde de l'endpoint qui l'exécute l'accepterait pour le connecté
 * (profil, paire de délégation active, identité nominative, localité) : la garde fait foi.</p>
 */
public enum GesteAFaire {
    /** Enregistrer la réception (numérotation) — {@code POST /api/receptions}. */
    NUMEROTER,
    /** Dispatcher — {@code POST /api/dispatchs}. */
    DISPATCHER,
    /** Examiner — {@code POST /api/examens}, puis {@code POST /api/examens/{id}/soumettre}. */
    EXAMINER,
    /** Réattribuer à un Membre — {@code PUT /api/dispatchs/{id}} (CC attributaire d'un dossier central). */
    REATTRIBUER,
    /** Réexaminer après lettre de renvoi. */
    REEXAMINER,
    /** Soumettre le projet de PV — {@code POST /api/pv-examens/{id}/soumettre}. */
    SOUMETTRE_PV,
    /** Reprendre l'examen retourné en rectification. */
    REPRENDRE_EXAMEN,
    /** Accepter et transmettre au Président — {@code POST /api/pv-examens/{id}/accepter}. */
    ACCEPTER,
    /** Retourner le projet — {@code POST /api/pv-examens/{id}/retourner}. */
    RETOURNER,
    /** Viser — {@code POST /api/pv-examens/{id}/viser}. */
    VISER,
    /** Signer sa part désignée — {@code POST /api/pv-examens/{id}/signer}. */
    SIGNER,
    /** Signer la lettre de renvoi — {@code POST /api/lettre-renvois/{id}/signer}. */
    SIGNER_LETTRE,
    /** Accepter ou refuser la demande de retrait. */
    DECIDER_RETRAIT,
    /** Statuer les observations (avis FAVR) — {@code POST /api/observations-pv/passage}. */
    VERIFIER,
    /** Transmettre la décision à SIGMP (avis FAV, DEF, NSP) — {@code POST /api/sigmp-transmissions}. */
    TRANSMETTRE_DECISION,
    /** Transmettre à SIGMP après levée des observations — {@code POST /api/sigmp-transmissions}. */
    TRANSMETTRE_SIGMP,
    /** Archiver le PV — {@code POST /api/pv-examens/{id}/archiver}. */
    ARCHIVER_PV,
    /** Archiver la lettre — {@code POST /api/lettre-renvois/{id}/archiver}. */
    ARCHIVER_LETTRE,
    /** Consulter un dossier en attente de la PRMP (aucune action possible). */
    VOIR,
    /** Soumettre le brouillon (PRMP) — {@code POST /api/dossiers/{id}/soumettre}. */
    SOUMETTRE,
    /** Compléter le brouillon (UGPM : préparation, sans soumission). */
    COMPLETER_BROUILLON,
    /** Déposer les pièces du dépôt puis les transmettre — {@code POST /api/dossiers/{id}/transmettre-complements-depot}. */
    COMPLETER_PIECES_DEPOT,
    /** Transmettre les compléments de la lettre de renvoi — {@code POST /api/dossiers/{id}/transmettre-complements}. */
    TRANSMETTRE_COMPLEMENTS,
    /** Rectifier puis resoumettre — {@code POST /api/dossiers/{id}/resoumettre}. */
    RECTIFIER,
    /** Suivre l'avancement à la CNM. */
    SUIVRE
}
