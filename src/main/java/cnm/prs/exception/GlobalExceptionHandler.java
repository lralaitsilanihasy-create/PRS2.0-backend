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

    /**
     * Verbe HTTP non supporté par la route → <strong>405</strong> avec l'en-tête {@code Allow}.
     *
     * <p>⚠️ Correction (2026-08-24) — sans ce gestionnaire, {@link HttpRequestMethodNotSupportedException}
     * était captée par le {@code @ExceptionHandler(Exception.class)} de cette classe <em>avant</em> que le
     * résolveur par défaut de Spring ne la voie : <strong>un mauvais verbe sur n'importe quelle route de
     * l'API répondait 500</strong>, message d'exception à l'appui. Un client ne pouvait donc pas distinguer
     * une route mal appelée d'une panne serveur, et l'API annonçait une défaillance là où elle avait
     * simplement un contrat. Le gestionnaire spécifique l'emporte sur celui d'{@code Exception} quel que
     * soit l'ordre de déclaration ; il est placé ici pour la lecture, entre les autres cas de protocole.</p>
     *
     * <p>{@code Allow} est peuplé depuis {@code getSupportedHttpMethods()}, qui peut être {@code null} ou
     * vide (route inexistante côté mapping) : dans ce cas l'en-tête est omis plutôt que rendu vide.</p>
     */
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            org.springframework.web.HttpRequestMethodNotSupportedException ex, WebRequest request) {
        ResponseEntity<ErrorResponse> reponse = build(HttpStatus.METHOD_NOT_ALLOWED,
                "Méthode " + ex.getMethod() + " non autorisée sur cette ressource.", request, null);
        java.util.Set<org.springframework.http.HttpMethod> autorisees = ex.getSupportedHttpMethods();
        if (autorisees == null || autorisees.isEmpty()) {
            return reponse;
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .allow(autorisees.toArray(new org.springframework.http.HttpMethod[0]))
                .body(reponse.getBody());
    }

    /**
     * Paramètre de requête (ou variable de chemin) d'un <strong>type incompatible</strong> —
     * {@code ?ppm=abc} sur un {@code Integer}, {@code ?lu=oui} sur un {@code Boolean},
     * {@code ?from=hier} sur une date → <strong>400</strong> nommant le paramètre et le type attendu.
     *
     * <p>⚠️ Correction (2026-08-25) — <em>même famille de défaut que le 405 traité ci-dessus</em> :
     * {@link org.springframework.web.method.annotation.MethodArgumentTypeMismatchException} est une
     * exception MVC de Spring, mais aucun gestionnaire ne la déclarait ici — elle tombait donc dans le
     * {@code @ExceptionHandler(Exception.class)} de cette classe, <em>avant</em> le résolveur par défaut,
     * et l'API répondait <strong>500 générique</strong>. L'appelant apprenait que le serveur avait planté
     * alors qu'il avait simplement mal formé sa requête : rien dans la réponse ne lui permettait de
     * corriger, et un moniteur de disponibilité comptait une 5xx à chaque paramètre mal tapé.</p>
     *
     * <p>La forme reste celle des autres 400 : message général, détail dans {@code erreurs[]} —
     * {@code champ} = le nom du paramètre tel qu'il figure dans l'URL, {@code message} = le type attendu.
     * Le front traite donc cette erreur avec le même code que celles de {@code @Valid}.</p>
     */
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex, WebRequest request) {
        String nom = ex.getName();
        List<ErrorResponse.FieldError> erreurs =
                List.of(new ErrorResponse.FieldError(nom, attenduPour(ex.getRequiredType())));
        return build(HttpStatus.BAD_REQUEST,
                "Paramètre « " + nom + " » invalide : valeur du mauvais type.", request, erreurs);
    }

    /**
     * Libellé du type attendu par un paramètre. Pour une énumération, les valeurs admises sont
     * <strong>énumérées</strong> : c'est la seule information qui permette à l'appelant de corriger sans
     * consulter la documentation — et c'est déjà ce que font les 400 métier ({@code ?statut=} inconnu).
     * Le type peut être {@code null} (Spring ne le renseigne pas toujours) : repli générique.
     */
    private static String attenduPour(Class<?> type) {
        if (type == null) {
            return "Valeur du mauvais type pour ce paramètre.";
        }
        if (type.isEnum()) {
            return "Valeur attendue parmi : " + java.util.Arrays.toString(type.getEnumConstants()) + ".";
        }
        if (Boolean.class.equals(type) || boolean.class.equals(type)) {
            return "Valeur booléenne attendue : true ou false.";
        }
        if (Number.class.isAssignableFrom(type) || (type.isPrimitive() && !char.class.equals(type))) {
            return "Valeur numérique attendue.";
        }
        if (java.time.temporal.Temporal.class.isAssignableFrom(type)
                || java.util.Date.class.isAssignableFrom(type)) {
            return "Date invalide : format attendu AAAA-MM-JJ.";
        }
        return "Valeur du mauvais type pour ce paramètre.";
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
        // Toute autre erreur JPA est un défaut serveur : même traitement générique que handleGeneric,
        // le message d'origine (souvent porteur de SQL et de noms de tables) ne sort pas dans le corps.
        return erreurInterne(ex, request);
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
            case "22001" -> {                                   // string_data_right_truncation
                // ⚠️ Correction (2026-08-24) — un dépassement de longueur tombait en `default` : 409
                // « Violation d'une contrainte de données », sans nommer le champ. C'est une faute de SAISIE,
                // corrigeable par l'appelant : elle relève du 400, comme la même valeur refusée en amont par
                // `@Size`. Le front recevait deux réponses incomparables pour une seule et même erreur.
                String champ = champTropLong(ex);
                yield build(HttpStatus.BAD_REQUEST, "Valeur trop longue pour un champ de cette ressource.", request,
                        champ == null ? null
                                : List.of(new ErrorResponse.FieldError(champ, "Valeur trop longue pour ce champ.")));
            }
            default -> build(HttpStatus.CONFLICT, "Violation d'une contrainte de données.", request, null);
        };
    }

    /**
     * Nom du champ (JSON) en cause dans un dépassement de longueur, ou {@code null} si le pilote ne le dit pas.
     *
     * <p>Deux sources, dans cet ordre : le {@code ServerErrorMessage} de pgjdbc (lu <strong>par réflexion</strong>,
     * comme pour Jackson — le pilote PostgreSQL n'est pas exposé à la compilation), puis, à défaut, le nom de
     * colonne cité dans le message ({@code Value too long for column "DESIGNATION_MARCHE …"} côté H2). La colonne
     * est ensuite convertie en nom de propriété : le modèle mappe les colonnes {@code SNAKE_MAJUSCULE} sur des
     * propriétés {@code camelCase} homonymes, et les DTO reprennent ces noms — la conversion suffit donc.
     * L'information reste <strong>facultative</strong> : sans elle, le 400 est rendu sans tableau {@code erreurs}.</p>
     */
    private static String champTropLong(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof java.sql.SQLException) {
                Object serveur = invoquer(t, "getServerErrorMessage");
                if (serveur != null && invoquer(serveur, "getColumn") instanceof String col && !col.isBlank()) {
                    return camel(col);
                }
            }
            if (t.getMessage() != null) {
                java.util.regex.Matcher m = COLONNE_CITEE.matcher(t.getMessage());
                if (m.find()) {
                    return camel(m.group(1));
                }
            }
        }
        return null;
    }

    /** Nom de colonne cité dans un message de dépassement de longueur (H2 : {@code for column "NOM …"}). */
    private static final java.util.regex.Pattern COLONNE_CITEE =
            java.util.regex.Pattern.compile("(?i)column\\s+\"?([A-Za-z_][A-Za-z0-9_]*)");

    /** {@code DESIGNATION_MARCHE} → {@code designationMarche} (convention de mapping du modèle). */
    private static String camel(String colonne) {
        String[] mots = colonne.toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder(mots[0]);
        for (int i = 1; i < mots.length; i++) {
            if (!mots[i].isEmpty()) {
                sb.append(Character.toUpperCase(mots[i].charAt(0))).append(mots[i].substring(1));
            }
        }
        return sb.toString();
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
     * Filet de sécurité : toute exception non traitée ci-dessus → 500 <strong>générique</strong>.
     *
     * <p>⚠️ Correction (2026-08-24) — le corps renvoyait {@code ex.getMessage()} brut. Selon l'exception, cela
     * publiait au client un fragment de requête SQL avec ses noms de tables et de colonnes, un chemin de
     * fichier du serveur ou un nom de classe interne : une carte du système offerte à qui sait provoquer une
     * erreur, sur une API dont certaines routes sont publiques. Le client reçoit désormais une phrase fixe ;
     * le détail n'est pas perdu pour autant — il part au journal ({@code ERROR} + trace complète), seul
     * endroit où il est exploitable sans être exposé.</p>
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, WebRequest request) {
        return erreurInterne(ex, request);
    }

    /** Message unique des 500 : aucun détail d'implémentation ne transite par le corps de réponse. */
    static final String MESSAGE_ERREUR_INTERNE =
            "Une erreur interne est survenue. L'incident a été enregistré ; réessayez plus tard.";

    /** Journalise l'exception complète (seule trace du détail) puis rend le 500 générique. */
    private ResponseEntity<ErrorResponse> erreurInterne(Exception ex, WebRequest request) {
        log.error("Erreur non traitée sur {}", request.getDescription(false).replace("uri=", ""), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, MESSAGE_ERREUR_INTERNE, request, null);
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
