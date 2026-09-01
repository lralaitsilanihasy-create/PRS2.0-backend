package cnm.prs.security;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.SecretKey;

import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.web.util.WebUtils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * ⚠️ CORRECTIF 2026-09-01 — jeton CSRF <strong>stable par session</strong>, au lieu d'un aléa régénéré.
 *
 * <p><strong>Le défaut.</strong> {@link CookieCsrfTokenRepository} tire un jeton <em>aléatoire</em>
 * chaque fois qu'une requête n'en porte pas. En <strong>rafale concurrente</strong> — la réconciliation
 * du front en émet dix-sept d'un coup à la reprise d'un examen — plusieurs requêtes partent avant que
 * le cookie ne soit posé : chacune fait générer un jeton DIFFÉRENT, la dernière réponse gagne le
 * cookie du navigateur, et les requêtes encore en vol portent un en-tête devenu obsolète. La garde
 * compare, ne reconnaît pas, et rejette. C'est ce qui faisait échouer une requête sur dix-sept sans
 * qu'aucune ne soit fautive.</p>
 *
 * <p><strong>Le remède.</strong> Le jeton n'est plus tiré : il est <em>dérivé</em> de la session, par
 * {@code HMAC-SHA256(secret, jeton de session)}. Deux requêtes concurrentes calculent donc la
 * <strong>même</strong> valeur — il n'y a plus rien à faire tourner, et la course disparaît au lieu
 * d'être atténuée. Non devinable sans le secret de l'application, renouvelé de fait à chaque session
 * (le JWT change), et sans aucun état côté serveur : la propriété stateless du double-submit est
 * conservée.</p>
 *
 * <p><strong>Hors session</strong> (aucun cookie {@code PRS_SESSION}), on retombe sur le comportement
 * standard : jeton aléatoire posé par le dépôt délégué. Ces requêtes ne sont de toute façon pas
 * protégées par {@link CookieCsrfGarde}, qui n'agit que sur le canal cookie.</p>
 */
public class JetonCsrfParSession implements CsrfTokenRepository {

    private static final String ALGO = "HmacSHA256";

    private final CookieCsrfTokenRepository delegue;
    private final SecretKey secret;

    public JetonCsrfParSession(CookieCsrfTokenRepository delegue, SecretKey secret) {
        this.delegue = delegue;
        this.secret = secret;
    }

    @Override
    public CsrfToken generateToken(HttpServletRequest request) {
        String session = jetonDeSession(request);
        if (session == null) {
            return delegue.generateToken(request);
        }
        CsrfToken modele = delegue.generateToken(request);
        return new DefaultCsrfToken(modele.getHeaderName(), modele.getParameterName(), derive(session));
    }

    @Override
    public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
        delegue.saveToken(token, request, response);
    }

    /**
     * ⚠️ Rend délibérément {@code null} quand une session existe, pour forcer le cycle
     * <em>generate + save</em> de {@code CsrfFilter}.
     *
     * <p>Rendre directement le jeton dérivé paraissait plus direct — c'était un piège. {@code CsrfFilter}
     * n'appelle {@link #saveToken} que lorsque {@code loadToken} n'a rien rendu : un jeton non nul
     * <strong>supprimait la réécriture du cookie</strong>, et le navigateur gardait la valeur aléatoire
     * posée au login, laquelle ne correspondait plus au jeton dérivé attendu — 403 sur toute mutation.
     * Le test du canal cookie l'a montré immédiatement.</p>
     *
     * <p>En rendant {@code null}, on obtient un cookie réécrit à chaque réponse avec la <strong>même</strong>
     * valeur dérivée : déterministe, donc sans course, et toujours synchronisé avec ce que le serveur
     * attend. On ne lit jamais le cookie entrant — s'y fier laisserait un cookie périmé d'une rafale
     * précédente faire autorité, ce qui rouvrirait exactement le défaut corrigé ici.</p>
     */
    @Override
    public CsrfToken loadToken(HttpServletRequest request) {
        return jetonDeSession(request) == null ? delegue.loadToken(request) : null;
    }

    private String jetonDeSession(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, SessionCookies.NOM);
        String valeur = cookie == null ? null : cookie.getValue();
        return valeur == null || valeur.isBlank() ? null : valeur;
    }

    private String derive(String jetonDeSession) {
        try {
            Mac mac = Mac.getInstance(ALGO);
            mac.init(secret);
            return HexFormat.of().formatHex(mac.doFinal(jetonDeSession.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            // Secret inutilisable : on ne dégrade pas silencieusement vers un jeton devinable.
            throw new IllegalStateException("Dérivation du jeton CSRF impossible : " + e.getMessage(), e);
        }
    }
}
