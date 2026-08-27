package cnm.prs.exception;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Message rendu au client pour toute erreur non prévue (500). Volontairement opaque :
     * le détail technique (message d'exception, pile) part dans les journaux serveur, jamais
     * dans la réponse HTTP — il renseignerait un attaquant sur la structure interne.
     */
    private static final String MESSAGE_ERREUR_INTERNE = "Une erreur interne est survenue.";

    /**
     * 404 métier : le message de {@link ResourceNotFoundException} est écrit par le service, en
     * français et sans détail technique — il est rendu tel quel.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, WebRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }

    /**
     * ⚠️ Audit 2026-08-27 (lot B) — 404 levé par <strong>JPA</strong>
     * ({@code getReference} sur un identifiant absent) : son message est celui d'Hibernate, en
     * anglais et portant le <strong>nom de la classe interne</strong>
     * (« Unable to find cnm.prs.entity.Dossier with id 42 ») — il partait tel quel au client, ce que
     * la politique de {@link #MESSAGE_ERREUR_INTERNE} interdit précisément pour les 500. Le détail
     * reste dans les journaux serveur ; le client reçoit un message générique en français.
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFound(EntityNotFoundException ex, WebRequest request) {
        log.warn("Entite JPA introuvable sur {} : {}", uri(request), ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "Ressource introuvable.", request, null);
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
     * <p>
     * ⚠️ Cette branche « must be manually assigned » est probablement morte depuis le LOT 3b
     * (séquences serveur généralisées, migration V5) : elle est conservée en garde, son coût
     * étant nul. Toute AUTRE {@code JpaSystemException} est une vraie 500 : journalisée avec
     * sa pile, rendue au client sous le message générique.
     */
    @ExceptionHandler(JpaSystemException.class)
    public ResponseEntity<ErrorResponse> handleJpaSystem(JpaSystemException ex, WebRequest request) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        if (message.contains("must be manually assigned")) {
            return build(HttpStatus.BAD_REQUEST,
                    "L'identifiant (clé primaire) est obligatoire à la création de cette ressource.",
                    request, null);
        }
        log.error("Erreur non prevue sur {} : ", uri(request), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, MESSAGE_ERREUR_INTERNE, request, null);
    }

    /**
     * Conflit de <strong>verrou optimiste</strong> (⚠️ LOT 4 — 2026-08-26, migration V6) : la ligne
     * a été modifiée par une autre transaction entre son chargement et son enregistrement. Hibernate
     * l'a détecté sur la colonne {@code VERSION} des six entités chaudes du circuit (Dossier, Ppm,
     * Marche, PvExamen, LettreRenvoi, DemandeRetrait) et a refusé l'écriture — sans ce verrou, la
     * seconde écriture aurait écrasé la première <em>silencieusement</em>.
     * <p>
     * → <strong>409</strong> : ce n'est pas une erreur serveur mais un conflit que l'appelant
     * résout en rechargeant. Journalisé en {@code warn} (pas d'{@code error} : le cas est prévu),
     * sans la pile, qui n'apprendrait rien.
     * <p>
     * ⚠️ Chantier « conflit de version » (2026-08-27, {@code docs/plan-conflit-version.md}) : la réponse
     * porte désormais le code {@link ConflitVersionException#CODE}, sans quoi le front ne pouvait pas
     * distinguer ce 409 des autres (règle métier, doublon, clé étrangère).
     */
    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleConflitVersion(
            org.springframework.orm.ObjectOptimisticLockingFailureException ex, WebRequest request) {
        log.warn("Conflit de verrou optimiste sur {} : {} (id {})",
                uri(request), ex.getPersistentClassName(), ex.getIdentifier());
        return build(HttpStatus.CONFLICT, ConflitVersionException.MESSAGE, request, null,
                ConflitVersionException.CODE);
    }

    /**
     * ⚠️ Chantier « conflit de version » — <strong>chemin HTTP</strong> : la version envoyée dans le
     * corps du PUT ne correspond plus à celle de l'entité en base ; le service a refusé l'écriture
     * <strong>avant</strong> de toucher quoi que ce soit (cf. {@link ConflitVersionException}).
     * <p>
     * Même corps que le chemin transactionnel ci-dessus — 409, même message, même code : le front
     * n'a qu'un seul cas à traiter. Journalisé en {@code warn} sans pile (cas prévu).
     */
    @ExceptionHandler(ConflitVersionException.class)
    public ResponseEntity<ErrorResponse> handleConflitVersionHttp(ConflitVersionException ex, WebRequest request) {
        log.warn("Conflit de version sur {} : {}", uri(request), ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, null, ConflitVersionException.CODE);
    }

    /**
     * Violation d'une contrainte de base : clé primaire en doublon, valeur obligatoire
     * manquante (NOT NULL) ou référence (clé étrangère) inexistante. → 409 plutôt qu'une 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, WebRequest request) {
        // Distingue la cause racine (SQLSTATE PostgreSQL) au lieu d'un message fourre-tout.
        String sqlState = sqlState(ex);
        // Les messages rendus au client (ci-dessous) restent volontairement génériques : c'est
        // ici, dans le journal, que se lit la contrainte réellement violée (nom d'index, colonne).
        log.warn("Violation de contrainte sur {} (SQLSTATE {}) : {}",
                uri(request), sqlState, ex.getMostSpecificCause().getMessage());
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

    /**
     * Filet de sécurité : toute exception non prise en charge plus haut. Le détail technique
     * est <strong>journalisé avec sa pile</strong> (seule trace exploitable pour le diagnostic,
     * puisque le client ne reçoit plus qu'un message générique).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, WebRequest request) {
        log.error("Erreur non prevue sur {} : ", uri(request), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, MESSAGE_ERREUR_INTERNE, request, null);
    }

    /** Chemin de la requête, tel que reporté dans le corps d'erreur. */
    private static String uri(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
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
