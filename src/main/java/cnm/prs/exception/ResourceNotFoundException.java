package cnm.prs.exception;

/**
 * Levée lorsqu'une ressource demandée n'existe pas. Mappée sur HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    /** ⚠️ 2026-10-19 (« copie-offre-candidat ») — un code facultatif, quand le front doit dire pourquoi la ressource manque. */
    private final String code;

    public ResourceNotFoundException(String message) {
        this(message, null);
    }

    public ResourceNotFoundException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
