package cnm.prs.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * ⚠️ Plan cookie HttpOnly, phase 1 (2026-08-17) — <strong>exécuteur</strong> de la garde CSRF du canal
 * cookie. Le {@code CsrfFilter} de Spring ne peut pas jouer ce rôle ici : le resource server OAuth2
 * ajoute d'office à ses exemptions un {@code BearerTokenRequestMatcher} basé sur le
 * {@code BearerTokenResolver} — et comme notre résolveur lit aussi le cookie {@code PRS_SESSION},
 * toute requête authentifiée par cookie serait exemptée du CSRF standard. Le {@code CsrfFilter} reste
 * donc l'<strong>émetteur</strong> du jeton (cookie {@code XSRF-TOKEN} posé à chaque réponse), et ce
 * filtre applique la vérification <strong>double-submit stateless</strong> sur le seul canal exposé :
 *
 * <p>Une requête <strong>mutante</strong> (hors GET/HEAD/OPTIONS/TRACE), <strong>hors
 * {@code /api/auth/**}</strong>, <strong>sans en-tête {@code Authorization}</strong> (non forgeable
 * cross-site — canal exempt) et <strong>portant le cookie de session</strong> doit présenter un
 * en-tête {@code X-XSRF-TOKEN} égal à la valeur du cookie {@code XSRF-TOKEN} (comparaison en temps
 * constant) — sinon <strong>403</strong>. Un site tiers peut déclencher l'envoi des cookies, mais ne
 * peut ni les lire ni poser l'en-tête : l'égalité prouve l'origine légitime. Angular {@code HttpClient}
 * pose l'en-tête automatiquement (mêmes noms par défaut).</p>
 */
public class CookieCsrfGarde extends OncePerRequestFilter {

    static final String COOKIE_XSRF = "XSRF-TOKEN";
    static final String EN_TETE_XSRF = "X-XSRF-TOKEN";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (aProteger(request) && !jetonValide(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Jeton CSRF absent ou invalide (en-tête X-XSRF-TOKEN attendu, égal au cookie XSRF-TOKEN).");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean aProteger(HttpServletRequest request) {
        String methode = request.getMethod();
        boolean sure = "GET".equals(methode) || "HEAD".equals(methode)
                || "OPTIONS".equals(methode) || "TRACE".equals(methode);
        return !sure
                && !request.getRequestURI().startsWith("/api/auth/")
                && request.getHeader(HttpHeaders.AUTHORIZATION) == null
                && WebUtils.getCookie(request, SessionCookies.NOM) != null;
    }

    private boolean jetonValide(HttpServletRequest request) {
        Cookie attendu = WebUtils.getCookie(request, COOKIE_XSRF);
        String recu = request.getHeader(EN_TETE_XSRF);
        if (attendu == null || attendu.getValue() == null || attendu.getValue().isBlank()
                || recu == null || recu.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(attendu.getValue().getBytes(StandardCharsets.UTF_8),
                recu.getBytes(StandardCharsets.UTF_8));
    }
}
