package cnm.prs.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Branche l'{@link AuditInterceptor} sur l'API (sauf l'authentification).
 */
@Configuration
public class AuditConfig implements WebMvcConfigurer {

    private final AuditInterceptor auditInterceptor;

    public AuditConfig(AuditInterceptor auditInterceptor) {
        this.auditInterceptor = auditInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auditInterceptor)
                .addPathPatterns("/api/**")
                // L'assistant IA journalise lui-même chaque échange, question et réponse comprises
                // (AssistantIaService) : la ligne générique de l'intercepteur ferait doublon.
                .excludePathPatterns("/api/auth/**", "/api/audit-logs/**", "/api/assistant-ia/**");
    }
}
