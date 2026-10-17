package cnm.prs.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * ⚠️ 2026-10-17 (demande front « specifications-donnees-et-plans-pdf », §B1) — la limite multipart de l'application passe à 50 Mo pour
 * le seul PDF du devis descriptif et des plans des travaux ({@code PUT /api/fiches-marche/{idDmc}/specifications}). Partout ailleurs,
 * la limite d'avant reste : 20 Mo par fichier, 25 Mo par requête, avec la même réponse (400 « Fichier trop volumineux », par
 * {@link MaxUploadSizeExceededException}) — beaucoup de dépôts n'ont pas d'autre contrôle de taille que celui-là.
 */
@Configuration
public class TailleFichiersConfig implements WebMvcConfigurer {

    static final long FICHIER_MAX = 20L * 1024 * 1024;
    static final long REQUETE_MAX = 25L * 1024 * 1024;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                // La requête peut être enveloppée (Spring Security) : la requête multipart se cherche dessous.
                MultipartHttpServletRequest m = org.springframework.web.util.WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class);
                if (m != null) {
                    if (request.getContentLengthLong() > REQUETE_MAX) {
                        throw new MaxUploadSizeExceededException(REQUETE_MAX);
                    }
                    for (MultipartFile f : m.getMultiFileMap().values().stream().flatMap(java.util.List::stream).toList()) {
                        if (f.getSize() > FICHIER_MAX) {
                            throw new MaxUploadSizeExceededException(FICHIER_MAX);
                        }
                    }
                }
                return true;
            }
        }).addPathPatterns("/**").excludePathPatterns("/api/fiches-marche/*/specifications");
    }
}
