package cnm.prs.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Branche l'{@link InterimContexteInterceptor} sur l'API (sauf l'authentification, qui n'a pas de connecté).
 */
@Configuration
public class InterimContexteConfig implements WebMvcConfigurer {

    private final InterimContexteInterceptor interceptor;

    public InterimContexteConfig(InterimContexteInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**");
    }
}
