package cnm.prs.exception;

/**
 * Fichier téléversé dépassant la limite applicative → HTTP <strong>413 Payload Too Large</strong>
 * (spec « Actualités » du 2026-08-18 : image &gt; 10 Mo).
 */
public class PayloadTropVolumineuxException extends RuntimeException {

    public PayloadTropVolumineuxException(String message) {
        super(message);
    }
}
