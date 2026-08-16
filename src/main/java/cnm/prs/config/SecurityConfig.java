package cnm.prs.config;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * Sécurité de l'API : authentification JWT (HMAC HS256) en mode stateless.
 *
 * <p>Tous les endpoints exigent désormais un jeton valide, sauf {@code /api/auth/**}
 * (connexion). Le rôle métier est porté par la claim {@code role} du jeton et exposé
 * comme autorité {@code ROLE_<PROFIL>} pour les futures règles {@code @PreAuthorize}
 * (activées via {@link EnableMethodSecurity}).</p>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Ressources de référence / paramétrage (§3.8 Module 03 ; §3.2 « pas d'accès aux
     * référentiels ») : chemins de collection. Leurs écritures sont réservées à
     * l'Administrateur ; les lectures restent ouvertes aux utilisateurs authentifiés.
     */
    private static final String[] REFERENTIELS = {
            "/api/localites", "/api/points-ctrls",
            "/api/regle-anomalies", "/api/regle-alertes", "/api/comptes", "/api/cat-comptes",
            "/api/entite-contracts", "/api/categorie-entites", "/api/delegation-profils", "/api/aviss", "/api/natures",
            "/api/mode-passations", "/api/type-dossiers", "/api/sous-type-dossiers", "/api/ministeres",
            "/api/profiles", "/api/capm", "/api/type-piece-jointes", "/api/type-dmc"
    };

    /** Mêmes ressources, ciblées par identifiant (pour PUT / DELETE). */
    private static final String[] REFERENTIELS_ID = {
            "/api/localites/*", "/api/points-ctrls/*",
            "/api/regle-anomalies/*", "/api/regle-alertes/*", "/api/comptes/*", "/api/cat-comptes/*",
            "/api/entite-contracts/*", "/api/categorie-entites/*", "/api/delegation-profils/*", "/api/aviss/*", "/api/natures/*",
            "/api/mode-passations/*", "/api/type-dossiers/*", "/api/sous-type-dossiers/*", "/api/ministeres/*",
            "/api/profiles/*", "/api/capm/*", "/api/type-piece-jointes/*", "/api/type-dmc/*"
    };

    /**
     * Gestion des comptes & de la hiérarchie (§3.8 Module 10) : contrôleurs, PRMP,
     * organigramme. Écriture réservée à l'Administrateur ; lecture ouverte (l'UI affiche
     * noms et hiérarchie). Les sessions ({@code /api/session-utilisateurs}) sont, elles,
     * entièrement réservées à l'Admin (cf. SessionUtilisateurController).
     */
    private static final String[] GESTION_COMPTES = {
            "/api/controleurs", "/api/prmps", "/api/organigrammes"
    };

    private static final String[] GESTION_COMPTES_ID = {
            "/api/controleurs/*", "/api/prmps/*", "/api/organigrammes/*"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationConverter converter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                // ⚠️ Audit front (2026-08-16) — en-têtes de sécurité sur TOUTES les réponses :
                // CSP (API JSON : rien à charger, frame-ancestors 'self'), HSTS (émis sur les requêtes
                // HTTPS — derrière un proxy TLS, transmettre X-Forwarded-Proto, cf. application.properties),
                // X-Content-Type-Options: nosniff et X-Frame-Options conservés des DÉFAUTS Spring Security
                // (frameOptions aligné sameOrigin sur frame-ancestors 'self').
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; object-src 'none'; frame-ancestors 'self'"))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .frameOptions(fo -> fo.sameOrigin()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        // ⚠️ Règle ajoutée (2026-07-26) — création d'entité contractante ouverte à la PRMP
                        // (import PPM : autorité hors périmètre → nouvelle entité + rattachement EN ATTENTE),
                        // EN PLUS de l'Admin. Doit précéder la règle REFERENTIELS (1er match gagne).
                        // PUT/DELETE /api/entite-contracts/* restent Administrateur (via REFERENTIELS_ID).
                        .requestMatchers(HttpMethod.POST, "/api/entite-contracts").hasAnyRole("PRMP", "ADMINISTRATEUR")
                        // ⚠️ Règle ajoutée (2026-07-29) — création d'un MINISTÈRE (et de son organigramme)
                        // ouverte à la PRMP : nouvelle entité à l'import dont le ministère d'appartenance
                        // manque au référentiel. PUT/DELETE restent Administrateur (règles génériques).
                        .requestMatchers(HttpMethod.POST, "/api/ministeres").hasAnyRole("PRMP", "ADMINISTRATEUR")
                        .requestMatchers(HttpMethod.POST, "/api/organigrammes").hasAnyRole("PRMP", "ADMINISTRATEUR")
                        // Référentiels & paramétrage : écriture réservée à l'Administrateur.
                        .requestMatchers(HttpMethod.POST, REFERENTIELS).hasRole("ADMINISTRATEUR")
                        .requestMatchers(HttpMethod.PUT, REFERENTIELS_ID).hasRole("ADMINISTRATEUR")
                        .requestMatchers(HttpMethod.DELETE, REFERENTIELS_ID).hasRole("ADMINISTRATEUR")
                        // Gestion des comptes & hiérarchie (Module 10) : écriture réservée à l'Administrateur.
                        .requestMatchers(HttpMethod.POST, GESTION_COMPTES).hasRole("ADMINISTRATEUR")
                        .requestMatchers(HttpMethod.PUT, GESTION_COMPTES_ID).hasRole("ADMINISTRATEUR")
                        .requestMatchers(HttpMethod.DELETE, GESTION_COMPTES_ID).hasRole("ADMINISTRATEUR")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecretKey jwtSecretKey(@Value("${app.jwt.secret}") String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey key) {
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /**
     * Convertit la claim {@code role} du jeton en autorité {@code ROLE_<role>}.
     * Un jeton sans rôle reconnu n'obtient aucune autorité (moindre privilège).
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            if (role == null || role.isBlank()) {
                return List.of();
            }
            return List.of(new SimpleGrantedAuthority("ROLE_" + role));
        });
        return converter;
    }
}
