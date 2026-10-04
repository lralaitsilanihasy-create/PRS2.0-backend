package cnm.prs.exception;

import org.springframework.security.access.AccessDeniedException;

/**
 * ⚠️ 2026-10-04 (arbitrages du pilote après le lot 4, §B1, §B2) — un 403 qui porte un <strong>code nommé</strong>
 * ({@code PIECE_RESERVEE_CAO}, {@code NON_PRESENT}…), pour que l'écran explique le refus. Reste une {@link AccessDeniedException} :
 * tout ce qui la traite déjà la traite encore.
 */
public class AccesReserveException extends AccessDeniedException {

    private final String code;

    public AccesReserveException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
