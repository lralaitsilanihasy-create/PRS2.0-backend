package cnm.prs.security;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * ⚠️ Plan cookie HttpOnly, phase 1 (2026-08-17, {@code docs/plan-cookie-httponly.md}) — fabrique du
 * cookie de session {@code PRS_SESSION} : il transporte <strong>le même JWT</strong> que l'en-tête
 * {@code Authorization: Bearer} (mêmes claims, même décodeur) — seul le transport change.
 *
 * <p>Attributs : {@code HttpOnly} (illisible par le JS — le vol de session par XSS vise ce point),
 * {@code Secure} (surchargeable en dev via {@code app.auth.cookie.secure} ; Chrome/Firefox acceptent
 * les cookies Secure sur {@code localhost}), {@code SameSite=Strict} (exige le front et l'API sur la
 * <strong>même origine</strong> — phase 0 du plan), {@code Path=/}, durée alignée sur l'expiration du
 * JWT ({@code app.jwt.expiration-seconds}).</p>
 */
@Component
public class SessionCookies {

    /** Nom du cookie de session (phase 4 : {@code __Host-prs-session} en prod TLS). */
    public static final String NOM = "PRS_SESSION";

    private final boolean secure;
    private final long expirationSeconds;

    public SessionCookies(@Value("${app.auth.cookie.secure:true}") boolean secure,
            @Value("${app.jwt.expiration-seconds}") long expirationSeconds) {
        this.secure = secure;
        this.expirationSeconds = expirationSeconds;
    }

    /** Cookie posé au login — durée de vie alignée sur celle du JWT qu'il transporte. */
    public ResponseCookie creer(String jeton) {
        return ResponseCookie.from(NOM, jeton)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofSeconds(expirationSeconds))
                .build();
    }

    /** Suppression (logout) : mêmes attributs, valeur vide, {@code Max-Age=0}. */
    public ResponseCookie suppression() {
        return ResponseCookie.from(NOM, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();
    }
}
