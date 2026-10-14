package cnm.prs.config;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import cnm.prs.service.EtapesCloses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * ⚠️ R9c (manuel révisé, lot 2, §B4 n° 15) — le verrou d'écriture des étapes closes, posé une fois pour toutes les routes : toute
 * écriture (hors GET, HEAD, OPTIONS) sous {@code /api/fiches-marche/{idDmc}/seance}, {@code …/evaluation} ou {@code …/ami}, et la
 * désignation du représentant de la CAO ({@code /api/cao/procedures/{idDmc}/representant}), passe par {@link EtapesCloses#exigerOuvertes}.
 * La reprise et la réattribution vivent sous {@code …/attribution} : elles ne sont pas visées. L'exception levée ici suit la gestion
 * d'erreurs ordinaire (409 à code).
 */
@Component
public class EtapesClosesInterceptor implements HandlerInterceptor {

    private static final Set<String> LECTURES = Set.of("GET", "HEAD", "OPTIONS");
    private static final Pattern ETAPES = Pattern.compile("^/api/fiches-marche/(\\d+)/(seance|evaluation|ami)(/.*)?$");
    private static final Pattern REPRESENTANT = Pattern.compile("^/api/cao/procedures/(\\d+)/representant$");

    private final EtapesCloses etapes;

    public EtapesClosesInterceptor(EtapesCloses etapes) {
        this.etapes = etapes;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (LECTURES.contains(request.getMethod())) {
            return true;
        }
        Long idDmc = idDmc(chemin(request));
        if (idDmc != null) {
            etapes.exigerOuvertes(idDmc);
        }
        return true;
    }

    /** L'identifiant de la procédure si le chemin est une écriture d'étape verrouillable ; {@code null} sinon. */
    static Long idDmc(String chemin) {
        for (Pattern p : new Pattern[] { ETAPES, REPRESENTANT }) {
            Matcher m = p.matcher(chemin);
            if (m.matches()) {
                return Long.valueOf(m.group(1));
            }
        }
        return null;
    }

    private static String chemin(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contexte = request.getContextPath();
        return contexte != null && !contexte.isEmpty() && uri.startsWith(contexte) ? uri.substring(contexte.length()) : uri;
    }
}
