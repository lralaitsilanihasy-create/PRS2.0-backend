package cnm.prs.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** ⚠️ R9c — branche l'{@link EtapesClosesInterceptor} sur les étapes de la procédure et la désignation du représentant de la CAO. */
@Configuration
public class EtapesClosesConfig implements WebMvcConfigurer {

    private final EtapesClosesInterceptor interceptor;

    public EtapesClosesConfig(EtapesClosesInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/fiches-marche/*/seance/**", "/api/fiches-marche/*/evaluation/**",
                "/api/fiches-marche/*/ami/**", "/api/fiches-marche/*/seance", "/api/fiches-marche/*/evaluation", "/api/fiches-marche/*/ami",
                "/api/cao/procedures/*/representant");
    }
}
