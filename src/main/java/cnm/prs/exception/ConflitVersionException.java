package cnm.prs.exception;

/**
 * ⚠️ Chantier « conflit de version » (cf. {@code docs/plan-conflit-version.md}) — la donnée a été
 * modifiée par une autre opération entre le moment où l'appelant l'a chargée et celui où il tente
 * de l'enregistrer.
 *
 * <p>Deux chemins mènent au même 409 :</p>
 * <ul>
 *   <li><strong>transactionnel</strong> : Hibernate détecte le conflit au flush sur la colonne
 *       {@code VERSION} et lève une {@code ObjectOptimisticLockingFailureException} (LOT 4,
 *       migration V6) ;</li>
 *   <li><strong>HTTP</strong> (cette exception) : le service compare la version portée par le DTO
 *       de la requête PUT à celle de l'entité en base, <strong>avant toute écriture</strong> — ce
 *       qui protège aussi deux formulaires ouverts dans deux navigateurs, cas que le verrou
 *       transactionnel seul ne voit pas.</li>
 * </ul>
 *
 * <p>La comparaison se fait <strong>explicitement en service</strong> : écrire {@code setVersion(...)}
 * sur une entité managée serait un contrôle silencieusement mort (Hibernate ignore l'écriture
 * manuelle d'un champ {@code @Version}).</p>
 *
 * <p>Rendu HTTP : <strong>409</strong> avec le code {@link #CODE} (cf. {@code GlobalExceptionHandler}).</p>
 */
public class ConflitVersionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Code d'erreur stable, destiné au front (toast « Donnée modifiée entre-temps » et rechargement de l'écran). */
    public static final String CODE = "CONFLIT_VERSION";

    /** Message par défaut, volontairement orienté utilisateur — identique sur les deux chemins. */
    public static final String MESSAGE =
            "La donnée a été modifiée par une autre opération entre-temps. Rechargez puis réessayez.";

    public ConflitVersionException() {
        super(MESSAGE);
    }

    public ConflitVersionException(String message) {
        super(message);
    }
}
