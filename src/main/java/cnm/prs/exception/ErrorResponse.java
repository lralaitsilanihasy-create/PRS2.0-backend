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
        String code,
        /**
         * ⚠️ Fiche marché, lot 1b (2026-09-23) — le dossier en cause d'un 409 qui renvoie ailleurs
         * ({@code DOSSIER_EXISTANT}, {@code FICHE_DEJA_LIEE}) : de quoi que le front y navigue. Absent sinon.
         */
        Integer idDossier,
        /**
         * ⚠️ Lot C (2026-09-27) — détails nommés d'un 409 à code stable ({@code DOSSIER_EN_EXAMEN} : {@code statut} ;
         * {@code FICHE_NON_REVISEE} : {@code versionSoumise}, {@code versionCourante}, {@code statutFiche}). Absent sinon.
         */
        java.util.Map<String, Object> details) {

    /** Erreur sans code métier (cas général) — le champ {@code code} reste absent du JSON. */
    public ErrorResponse(LocalDateTime timestamp, int status, String error, String message, String path,
            List<FieldError> erreurs) {
        this(timestamp, status, error, message, path, erreurs, null, null, null);
    }

    public ErrorResponse(LocalDateTime timestamp, int status, String error, String message, String path,
            List<FieldError> erreurs, String code) {
        this(timestamp, status, error, message, path, erreurs, code, null, null);
    }

    public ErrorResponse(LocalDateTime timestamp, int status, String error, String message, String path,
            List<FieldError> erreurs, String code, Integer idDossier) {
        this(timestamp, status, error, message, path, erreurs, code, idDossier, null);
    }

    /** Détail d'un champ en cause lors d'une erreur de validation. */
    public record FieldError(String champ, String message) {
    }
}
