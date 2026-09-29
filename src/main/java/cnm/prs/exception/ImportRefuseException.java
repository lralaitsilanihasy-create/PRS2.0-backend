package cnm.prs.exception;

import org.springframework.http.HttpStatus;

/**
 * ⚠️ Import du DAO (demande front du 2026-09-28, §B1 ; ADR-0012) — un refus de lecture qui n'est ni un conflit d'état ni
 * une saisie fautive, avec son statut et son code stable : <strong>415</strong> {@code FORMAT_NON_SUPPORTE} (pas un Word
 * {@code .docx} lisible et sans macros), <strong>422</strong> {@code MODELE_ABSENT} (aucun modèle du lot D pour la forme et
 * la catégorie de la fiche).
 */
public class ImportRefuseException extends RuntimeException {

    public static final String FORMAT_NON_SUPPORTE = "FORMAT_NON_SUPPORTE";
    public static final String MODELE_ABSENT = "MODELE_ABSENT";
    public static final String DOCUMENT_SANS_TEXTE = "DOCUMENT_SANS_TEXTE";

    private final HttpStatus status;
    private final String code;

    public ImportRefuseException(HttpStatus status, String message, String code) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ImportRefuseException format(String message) {
        return new ImportRefuseException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, message, FORMAT_NON_SUPPORTE);
    }

    public static ImportRefuseException modeleAbsent() {
        return new ImportRefuseException(HttpStatus.UNPROCESSABLE_ENTITY,
                "L'import n'est pas encore possible pour ce type de marché : saisissez la fiche.", MODELE_ABSENT);
    }

    /**
     * ⚠️ Lot D2 (2026-09-29, §B5 règle 4) — un PDF sans texte (scanné) : pas d'OCR, rien à lire. 422
     * {@code DOCUMENT_SANS_TEXTE}.
     */
    public static ImportRefuseException sansTexte() {
        return new ImportRefuseException(HttpStatus.UNPROCESSABLE_ENTITY, "Document sans texte : saisissez la fiche.",
                DOCUMENT_SANS_TEXTE);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
