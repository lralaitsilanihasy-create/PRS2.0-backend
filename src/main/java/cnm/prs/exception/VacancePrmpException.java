package cnm.prs.exception;

/**
 * ⚠️ Règle ajoutée (spec « Mandats PRMP ») — <strong>standby de transition</strong> : aucune PRMP n'est
 * en fonction à la date de l'action (mandat achevé ou abrogé, successeur pas encore nommé).
 *
 * <p>Il n'y a <strong>aucune obligation d'intérim</strong> : le traitement s'arrête et attend. Le blocage
 * se lève <strong>automatiquement</strong> dès qu'un mandat redevient actif — rien à rejouer, rien à
 * débloquer manuellement. L'action en attente sera faite par le nouveau titulaire en tant qu'opérateur,
 * sans toucher à l'attribution des dossiers.</p>
 *
 * <p>Rendu HTTP : <strong>409</strong> avec le code {@link #CODE} (cf. {@code GlobalExceptionHandler}).</p>
 */
public class VacancePrmpException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Code d'erreur stable, destiné au front (affichage d'un bandeau « en attente de nomination »). */
    public static final String CODE = "VACANCE_PRMP";

    /** Message par défaut, volontairement orienté utilisateur. */
    public static final String MESSAGE = "En attente de nomination de la nouvelle PRMP";

    public VacancePrmpException() {
        super(MESSAGE);
    }

    public VacancePrmpException(String message) {
        super(message);
    }
}
