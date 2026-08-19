package cnm.prs.exception;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import jakarta.persistence.EntityNotFoundException;

/**
 * Gestion centralisée des erreurs de l'API REST.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({ ResourceNotFoundException.class, EntityNotFoundException.class })
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException ex, WebRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleException ex, WebRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex, WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }

    /**
     * ⚠️ Règle ajoutée (spec « Mandats PRMP ») — standby de transition : 409 portant le code
     * {@link VacancePrmpException#CODE}, que le front reconnaît pour afficher l'attente de nomination
     * plutôt qu'une erreur générique.
     */
    @ExceptionHandler(VacancePrmpException.class)
    public ResponseEntity<ErrorResponse> handleVacancePrmp(VacancePrmpException ex, WebRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, null, VacancePrmpException.CODE);
    }

    /** Fichier téléversé dépassant la limite multipart du serveur → 400 (pas de 500). */
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUpload(
            org.springframework.web.multipart.MaxUploadSizeExceededException ex, WebRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "Fichier trop volumineux : la taille maximale autorisée est dépassée.", request, null);
    }

    /** Limite applicative de taille de fichier dépassée → 413 (spec « Actualités », image > 10 Mo). */
    @ExceptionHandler(PayloadTropVolumineuxException.class)
    public ResponseEntity<ErrorResponse> handlePayloadTropVolumineux(
            PayloadTropVolumineuxException ex, WebRequest request) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, ex.getMessage(), request, null);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex, WebRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request, null);
    }

    @ExceptionHandler(ChampsInvalidesException.class)
    public ResponseEntity<ErrorResponse> handleChampsInvalides(ChampsInvalidesException ex, WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, ex.getErreurs());
    }

    /**
     * Corps de requête illisible / mal formé (JSON invalide, mauvais type, date hors format ISO).
     * → 400 avec le champ fautif dans {@code erreurs}, au lieu d'une 500 opaque. Le champ et le type
     * attendu sont lus <strong>par réflexion</strong> sur l'exception Jackson sous-jacente
     * ({@code MismatchedInputException#getPath()} / {@code InvalidFormatException#getTargetType()}) :
     * jackson-databind est présent à l'exécution mais non exposé à la compilation par le starter webmvc.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex, WebRequest request) {
        List<ErrorResponse.FieldError> erreurs = null;
        String champ = champFautif(ex);
        if (champ != null) {
            erreurs = List.of(new ErrorResponse.FieldError(champ, messageFautif(ex)));
        }
        return build(HttpStatus.BAD_REQUEST, "Corps de requête invalide ou mal formé.", request, erreurs);
    }

    /** Nom du champ fautif (feuille) lu sur le chemin Jackson {@code getPath()}, via sa notation {@code ["champ"]}. */
    private static String champFautif(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (invoquer(t, "getPath") instanceof List<?> refs && !refs.isEmpty()) {
                java.util.regex.Matcher m = REF_CHAMP.matcher(refs.toString());
                String dernier = null;
                while (m.find()) {
                    dernier = m.group(1);   // dernière feuille (ex. dateSignature, dateFin)
                }
                if (dernier != null) {
                    return dernier;
                }
            }
        }
        return null;
    }

    /** Notation d'un segment de chemin Jackson : {@code ["nomDuChamp"]} (stable Jackson 2 et 3). */
    private static final java.util.regex.Pattern REF_CHAMP = java.util.regex.Pattern.compile("\\[\"([^\"]+)\"\\]");

    /** Message selon le type attendu ({@code InvalidFormatException#getTargetType()}), lu par réflexion. */
    private static String messageFautif(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (invoquer(t, "getTargetType") instanceof Class<?> type) {
                if (java.time.temporal.Temporal.class.isAssignableFrom(type)) {
                    return "Date invalide : format attendu AAAA-MM-JJ.";
                }
                if (Number.class.isAssignableFrom(type)) {
                    return "Valeur numérique attendue pour ce champ.";
                }
                return "Valeur invalide pour ce champ.";
            }
        }
        return "Valeur invalide ou mal formatée pour ce champ.";
    }

    /** Invoque sans argument une méthode publique si elle existe (sinon {@code null}) — accès Jackson sans dépendance compile. */
    private static Object invoquer(Object cible, String methode) {
        try {
            return cible.getClass().getMethod(methode).invoke(cible);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
        List<ErrorResponse.FieldError> erreurs = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Validation échouée", request, erreurs);
    }

    /**
     * Identifiant (clé primaire assignée) manquant à la création : les entités du modèle
     * n'auto-génèrent pas leur PK, le client doit la fournir. → 400 plutôt qu'une 500 opaque.
     */
    @ExceptionHandler(JpaSystemException.class)
    public ResponseEntity<ErrorResponse> handleJpaSystem(JpaSystemException ex, WebRequest request) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        if (message.contains("must be manually assigned")) {
            return build(HttpStatus.BAD_REQUEST,
                    "L'identifiant (clé primaire) est obligatoire à la création de cette ressource.",
                    request, null);
        }
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), request, null);
    }

    /**
     * Violation d'une contrainte de base : clé primaire en doublon, valeur obligatoire
     * manquante (NOT NULL) ou référence (clé étrangère) inexistante. → 409 plutôt qu'une 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, WebRequest request) {
        // Distingue la cause racine (SQLSTATE PostgreSQL) au lieu d'un message fourre-tout.
        String sqlState = sqlState(ex);
        return switch (sqlState == null ? "" : sqlState) {
            case "23503" -> build(HttpStatus.CONFLICT,          // foreign_key_violation (insert : parent absent ; ou delete : enfant présent)
                    "Violation de clé étrangère : une donnée référencée est absente, ou cet enregistrement est encore référencé par d'autres.", request, null);
            case "23505" -> build(HttpStatus.CONFLICT,          // unique_violation
                    "Doublon : un enregistrement avec cette clé existe déjà.", request, null);
            case "23502" -> build(HttpStatus.BAD_REQUEST,       // not_null_violation
                    "Valeur obligatoire manquante.", request, null);
            default -> build(HttpStatus.CONFLICT, "Violation d'une contrainte de données.", request, null);
        };
    }

    /** Remonte la chaîne des causes jusqu'à un {@link java.sql.SQLException} pour lire son SQLSTATE. */
    private static String sqlState(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof java.sql.SQLException se) {
                return se.getSQLState();
            }
        }
        return null;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, WebRequest request) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), request, null);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, WebRequest request,
            List<ErrorResponse.FieldError> erreurs) {
        return build(status, message, request, erreurs, null);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, WebRequest request,
            List<ErrorResponse.FieldError> erreurs, String code) {
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getDescription(false).replace("uri=", ""),
                erreurs,
                code);
        return ResponseEntity.status(status).body(body);
    }
}
