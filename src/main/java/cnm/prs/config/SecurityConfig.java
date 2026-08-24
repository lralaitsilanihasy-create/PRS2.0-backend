package cnm.prs.config;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
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
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.util.WebUtils;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import cnm.prs.security.CookieCsrfGarde;
import cnm.prs.security.SessionCookies;
import jakarta.servlet.http.Cookie;

/**
 * Sécurité de l'API : authentification JWT (HMAC HS256) en mode stateless.
 *
 * <p>Tous les endpoints exigent désormais un jeton valide, sauf {@code /api/auth/**}
 * (connexion, auto-inscription, référentiel public des entités) — à l'exception de
 * {@code GET /api/auth/prmps}, extrait du {@code permitAll} et réservé à l'Administrateur.
 * Le rôle métier est porté par la claim {@code role} du jeton et exposé
 * comme autorité {@code ROLE_<PROFIL>} pour les futures règles {@code @PreAuthorize}
 * (activées via {@link EnableMethodSecurity}).</p>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /**
     * Valeur de repli d'{@code app.jwt.secret} dans {@code application.properties} : elle est
     * versionnée, donc publiquement lisible. Tolérée en local, refusée sous le profil {@code prod}.
     */
    private static final String JWT_SECRET_DEV = "dev-secret-please-change-0123456789-abcdefghij";

    /** Taille minimale de la clé HMAC pour HS256 : 256 bits (RFC 7518 § 3.2). */
    private static final int JWT_SECRET_MIN_OCTETS = 32;

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
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationConverter converter,
            BearerTokenResolver bearerTokenResolver) throws Exception {
        http
                // ⚠️ Plan cookie HttpOnly, phase 1 (2026-08-17) — CSRF en deux pièces :
                // 1) le CsrfFilter de Spring est l'ÉMETTEUR du jeton — CookieCsrfTokenRepository pose
                //    le cookie XSRF-TOKEN (lisible par le front) dès la première réponse (chargement
                //    immédiat via csrfRequestAttributeName null). Il n'APPLIQUE rien : le resource
                //    server OAuth2 exempte d'office de son enforcement toute requête où le
                //    BearerTokenResolver trouve un jeton — cookie de session compris (voir la garde) ;
                // 2) CookieCsrfGarde (ci-dessous, addFilterAfter) est l'EXÉCUTEUR : double-submit
                //    stateless X-XSRF-TOKEN == XSRF-TOKEN sur les mutations authentifiées PAR COOKIE
                //    uniquement (Authorization: Bearer exempt — en-tête non forgeable cross-site, la
                //    suite de tests reste inchangée ; requêtes sans cookie de session exemptes — les
                //    mutations anonymes restent des 401 ; /api/auth/** exempt). Angular pose l'en-tête
                //    automatiquement (mêmes noms par défaut).
                .csrf(csrf -> {
                    CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
                    handler.setCsrfRequestAttributeName(null);
                    csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                            .csrfTokenRequestHandler(handler)
                            .ignoringRequestMatchers("/api/auth/**")
                            .ignoringRequestMatchers(request ->
                                    request.getHeader(HttpHeaders.AUTHORIZATION) != null
                                            || WebUtils.getCookie(request, SessionCookies.NOM) == null);
                })
                .addFilterAfter(new CookieCsrfGarde(), org.springframework.security.web.csrf.CsrfFilter.class)
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
                        // ⚠️ Durcissement (2026-08-24) — GET /api/auth/prmps sort du permitAll.
                        //    Il servait publiquement le référentiel réduit des PRMP (idPrmp, nomPrmp,
                        //    prenomsPrmp), c'est-à-dire la liste des comptes de connexion existants,
                        //    alors qu'aucune limitation de débit ne protège POST /api/auth/login :
                        //    énumération de comptes + martelage illimité. Aucun consommateur : la
                        //    méthode AuthService#prmpsPubliques du front Angular n'est appelée nulle
                        //    part et l'écran d'inscription UGPM n'existe pas encore. Réservé à
                        //    l'ADMINISTRATEUR — seul profil dont le métier suppose le référentiel
                        //    GLOBAL des PRMP (rattachement de tutelle UGPM, gestion des comptes) ;
                        //    les autres profils passent par /api/prmps/par-localite ou /par-entite.
                        //    DOIT précéder /api/auth/** : le 1er matcher qui correspond gagne.
                        .requestMatchers(HttpMethod.GET, "/api/auth/prmps").hasRole("ADMINISTRATEUR")
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
                .oauth2ResourceServer(oauth -> oauth
                        .bearerTokenResolver(bearerTokenResolver)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter)));
        return http.build();
    }

    /**
     * ⚠️ Plan cookie HttpOnly, phase 1 (2026-08-17) — résolution du jeton : l'en-tête
     * {@code Authorization: Bearer} d'abord (clients API, tests d'intégration — canal conservé
     * définitivement), sinon le cookie de session {@code PRS_SESSION}. Le JWT transporté est LE MÊME
     * dans les deux canaux (mêmes claims, même décodeur) : seul le transport diffère.
     */
    @Bean
    public BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver enTete = new DefaultBearerTokenResolver();
        return request -> {
            String jeton = enTete.resolve(request);
            if (jeton != null) {
                return jeton;
            }
            Cookie cookie = WebUtils.getCookie(request, SessionCookies.NOM);
            return cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()
                    ? null : cookie.getValue();
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Construit la clé de signature des jetons, en refusant les configurations dangereuses.
     *
     * <p>Deux garde-fous, parce qu'un secret de signature faible ou connu permet de <b>forger un
     * jeton pour n'importe quel profil</b> :</p>
     * <ul>
     *   <li><b>Longueur</b> : HS256 exige une clé de 256 bits au minimum (RFC 7518 § 3.2). Un
     *       secret trop court échouait jusqu'ici dans les entrailles de Nimbus, à la première
     *       signature ; il échoue désormais au démarrage, avec le motif exact.</li>
     *   <li><b>Secret de développement</b> : la valeur de repli d'{@code application.properties}
     *       est publiquement lisible dans le dépôt. Sous le profil {@code prod} elle interdit le
     *       démarrage ; ailleurs elle est tolérée (poste de développement) mais signalée.</li>
     * </ul>
     */
    @Bean
    public SecretKey jwtSecretKey(@Value("${app.jwt.secret}") String secret, Environment env) {
        byte[] octets = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (octets.length < JWT_SECRET_MIN_OCTETS) {
            throw new IllegalStateException("app.jwt.secret fait " + octets.length
                    + " octet(s) : HS256 en exige au moins " + JWT_SECRET_MIN_OCTETS
                    + ". Définir la variable d'environnement APP_JWT_SECRET.");
        }
        if (JWT_SECRET_DEV.equals(secret)) {
            if (List.of(env.getActiveProfiles()).contains("prod")) {
                throw new IllegalStateException("app.jwt.secret est le secret de développement,"
                        + " publiquement lisible dans le dépôt : n'importe qui pourrait forger un"
                        + " jeton. Définir la variable d'environnement APP_JWT_SECRET.");
            }
            log.error("app.jwt.secret est le secret de developpement, publiquement lisible dans le"
                    + " depot. Acceptable en local uniquement : tout deploiement doit definir"
                    + " APP_JWT_SECRET (profil prod : demarrage refuse).");
        }
        return new SecretKeySpec(octets, "HmacSHA256");
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
