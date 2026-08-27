package cnm.prs.exception;

/**
 * ⚠️ Audit 2026-08-27 (lot E) — quota de débit dépassé sur une route publique
 * ({@code /api/auth/login}, {@code /api/auth/register/**}) : la demande est refusée
 * <strong>429 Too Many Requests</strong> par {@link GlobalExceptionHandler}, sans que les
 * identifiants soient seulement vérifiés.
 *
 * <p>Le message est écrit par {@code LoginRateLimiter}, en français et destiné à l'utilisateur :
 * il annonce le délai à attendre. {@link #getSecondesAvantReprise()} porte le même délai en
 * secondes, rendu dans l'en-tête HTTP {@code Retry-After} — la forme que les clients savent lire.</p>
 */
public class TropDeRequetesException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final long secondesAvantReprise;

    public TropDeRequetesException(String message, long secondesAvantReprise) {
        super(message);
        this.secondesAvantReprise = secondesAvantReprise;
    }

    /** Secondes avant la prochaine tentative permise (en-tête {@code Retry-After}). */
    public long getSecondesAvantReprise() {
        return secondesAvantReprise;
    }
}
