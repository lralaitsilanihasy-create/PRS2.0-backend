package cnm.prs.exception;

/**
 * Levée lorsqu'une donnée fournie par le client est invalide au regard d'une règle métier
 * (hors validation @Valid). Traduite en HTTP 400.
 *
 * <p>⚠️ 2026-10-04 (soumission en ligne, lot 1a) — un {@code code} stable facultatif, servi dans {@code ErrorResponse.code}
 * (ex. {@code CODE_INVALIDE}, {@code CODE_EXPIRE}), comme pour les 409 de {@link BusinessRuleException}.</p>
 */
public class BadRequestException extends RuntimeException {

    private final String code;

    public BadRequestException(String message) {
        this(message, null);
    }

    public BadRequestException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
