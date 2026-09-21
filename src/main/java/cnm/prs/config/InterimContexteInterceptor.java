package cnm.prs.config;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import cnm.prs.security.CurrentUser;
import cnm.prs.security.InterimContexte;
import cnm.prs.service.InterimService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * ⚠️ Intérim désigné (lot 1, 2026-09-21) — pose, <strong>une fois par requête</strong> et après
 * l'authentification, les suppléances actives du connecté dans {@link InterimContexte}, et les efface en
 * fin de requête.
 *
 * <p>Intercepteur MVC et non filtre de servlet : il s'exécute après la chaîne de sécurité (le jeton est lu),
 * et avant le contrôleur — donc avant les {@code @PreAuthorize} de ses méthodes, que
 * {@code PermissionService} évalue en lisant ce contexte. Une seule requête SQL, et seulement pour les
 * profils qui peuvent être intérimaires ({@link InterimService#PROFILS_INTERIMAIRES}) : pour tout autre,
 * rien n'est lu.</p>
 *
 * <p>Le contexte ne casse jamais la requête : une résolution qui échouerait laisse le contexte vide (aucun
 * droit étendu), sans rien remonter.</p>
 */
@Component
public class InterimContexteInterceptor implements HandlerInterceptor {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(InterimContexteInterceptor.class);

    private final InterimService interimService;

    public InterimContexteInterceptor(InterimService interimService) {
        this.interimService = interimService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            InterimContexte.poser(interimService.suppleancesActivesDe(
                    CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null),
                    CurrentUser.profil().orElse(null)));
        } catch (RuntimeException ex) {
            InterimContexte.effacer();
            LOG.warn("[INTERIM] contexte non resolu pour {} : {}", CurrentUser.ref().orElse("?"), ex.toString());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception ex) {
        InterimContexte.effacer();
    }
}
