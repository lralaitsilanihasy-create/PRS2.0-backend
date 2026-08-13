package cnm.prs.exception;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Corps JSON standardisé renvoyé en cas d'erreur.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> erreurs,
        String code) {

    /** Erreur sans code métier (cas général) — le champ {@code code} reste absent du JSON. */
    public ErrorResponse(LocalDateTime timestamp, int status, String error, String message, String path,
            List<FieldError> erreurs) {
        this(timestamp, status, error, message, path, erreurs, null);
    }

    /** Détail d'un champ en cause lors d'une erreur de validation. */
    public record FieldError(String champ, String message) {
    }
}
