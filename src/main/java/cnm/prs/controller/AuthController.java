package cnm.prs.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import cnm.prs.dto.EntitePubliqueDto;
import cnm.prs.dto.LoginRequest;
import cnm.prs.dto.LoginResponse;
import cnm.prs.dto.PrmpPubliqueDto;
import cnm.prs.dto.RegisterPrmpRequest;
import cnm.prs.dto.RegisterPrmpV2Request;
import cnm.prs.dto.RegisterResponse;
import cnm.prs.dto.RegisterUgpmRequest;
import cnm.prs.security.LoginRateLimiter;
import cnm.prs.security.SessionCookies;
import cnm.prs.service.AuthService;
import cnm.prs.service.EntiteContractService;

/**
 * Authentification : émission de jetons JWT. Endpoint public (cf. SecurityConfig).
 *
 * <p>⚠️ Audit 2026-08-27 (lot E) — toutes les routes publiques de ce contrôleur qui <em>coûtent</em>
 * (vérification BCrypt, création de fiche, stockage de fichier) passent par
 * {@link LoginRateLimiter}. La limitation est posée <strong>ici</strong> et non dans un filtre :
 * le quota du login a besoin de l'identifiant tenté, qui n'est connu qu'une fois le corps JSON
 * désérialisé, et le 429 traverse alors le {@code @RestControllerAdvice} comme toutes les autres
 * erreurs de l'API — un seul format de corps ({@code ErrorResponse}) pour le front.</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService service;
    private final EntiteContractService entiteContractService;
    private final SessionCookies sessionCookies;
    private final LoginRateLimiter limiteur;
    /** Phase 3 du plan cookie : à {@code true}, le jeton ne sort plus dans le corps du login. */
    private final boolean cookieExclusif;

    public AuthController(AuthService service, EntiteContractService entiteContractService,
            SessionCookies sessionCookies, LoginRateLimiter limiteur,
            @Value("${app.auth.cookie.exclusif:false}") boolean cookieExclusif) {
        this.service = service;
        this.entiteContractService = entiteContractService;
        this.sessionCookies = sessionCookies;
        this.limiteur = limiteur;
        this.cookieExclusif = cookieExclusif;
    }

    /**
     * Connexion. ⚠️ Plan cookie HttpOnly, phase 1 (2026-08-17) : le JWT est AUSSI posé en cookie de
     * session {@code PRS_SESSION} (HttpOnly, SameSite=Strict — voir {@link SessionCookies}). Tant que
     * {@code app.auth.cookie.exclusif} est {@code false}, le jeton reste dans le corps (transition
     * Bearer, rétro-compatible) ; à {@code true} (phase 3), le corps n'en porte plus.
     *
     * <p>⚠️ Audit 2026-08-27 (lot E) — encadrée par {@link LoginRateLimiter} : le quota est vérifié
     * <strong>avant</strong> que les identifiants ne soient examinés (→ 429), un échec l'incrémente,
     * un succès le remet à zéro. Seule une {@link BadCredentialsException} compte comme échec :
     * un refus métier (mandat vacant, par exemple) n'est pas une tentative d'intrusion.</p>
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest requete) {
        String ip = adresse(requete);
        limiteur.verifierLogin(ip, request.login());
        LoginResponse reponse;
        try {
            reponse = service.login(request);
        } catch (BadCredentialsException e) {
            limiteur.echecLogin(ip, request.login());
            throw e;
        }
        limiteur.succesLogin(ip, request.login());
        LoginResponse corps = cookieExclusif
                ? new LoginResponse(null, reponse.login(), reponse.role(), reponse.typeActeur(),
                        reponse.ref(), reponse.nomAffichage(), reponse.localite(), reponse.expiresIn())
                : reponse;
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionCookies.creer(reponse.token()).toString())
                .body(corps);
    }

    /**
     * ⚠️ Plan cookie HttpOnly, phase 1 — déconnexion : vide le cookie de session (un cookie HttpOnly
     * n'est pas supprimable par le JS du front). Route publique ({@code /api/auth/**}) : appelable
     * même avec une session expirée.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessionCookies.suppression().toString())
                .build();
    }

    /**
     * Référentiel public des entités contractantes (vue réduite), pour le formulaire
     * d'inscription PRMP. Route publique (cf. SecurityConfig : {@code /api/auth/**}).
     */
    @GetMapping("/entites")
    public List<EntitePubliqueDto> entites() {
        return entiteContractService.listePublique();
    }

    /**
     * Référentiel public réduit des PRMP (id + nom), pour le menu « PRMP de tutelle » du
     * formulaire d'inscription UGPM. Route publique (miroir de {@code GET /api/auth/entites}).
     */
    @GetMapping("/prmps")
    public List<PrmpPubliqueDto> prmps() {
        return service.prmpsPubliques();
    }

    /**
     * Auto-inscription d'une PRMP (variante JSON historique, route publique). Conservée le temps
     * de la bascule du frontend vers la v2 multipart ; sera retirée ensuite.
     */
    @PostMapping(value = "/register/prmp", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RegisterResponse> registerPrmp(@Valid @RequestBody RegisterPrmpRequest request,
            HttpServletRequest requete) {
        limiteur.consommerInscription(adresse(requete));
        return ResponseEntity.status(HttpStatus.CREATED).body(service.registerPrmp(request));
    }

    /**
     * Auto-inscription d'une PRMP v2 (route publique, {@code multipart/form-data}) : part JSON
     * {@code data} + fichiers {@code arrete} et {@code cin} (obligatoires) et {@code photo}
     * (optionnel). Crée un compte <strong>EN_ATTENTE</strong> avec ses entités déclarées et ses
     * pièces ; la connexion n'est possible qu'après validation par l'Administrateur.
     */
    @PostMapping(value = "/register/prmp", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RegisterResponse> registerPrmpV2(
            @Valid @RequestPart("data") RegisterPrmpV2Request data,
            @RequestPart("arrete") MultipartFile arrete,
            @RequestPart("cin") MultipartFile cin,
            @RequestPart(value = "photo", required = false) MultipartFile photo,
            HttpServletRequest requete) {
        limiteur.consommerInscription(adresse(requete));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.registerPrmpV2(data, arrete, cin, photo));
    }

    /**
     * Auto-inscription d'une UGPM (route publique, {@code multipart/form-data}) : part JSON
     * {@code data} ({@code RegisterUgpmRequest}) + fichiers {@code cin} (obligatoire) et
     * {@code photo} (optionnel). Miroir de l'inscription PRMP sans arrêté ni entités : crée un
     * compte <strong>EN_ATTENTE</strong> ; connexion possible après validation par l'Administrateur.
     */
    @PostMapping(value = "/register/ugpm", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RegisterResponse> registerUgpm(
            @Valid @RequestPart("data") RegisterUgpmRequest data,
            @RequestPart("cin") MultipartFile cin,
            @RequestPart(value = "photo", required = false) MultipartFile photo,
            HttpServletRequest requete) {
        limiteur.consommerInscription(adresse(requete));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.registerUgpm(data, cin, photo));
    }

    /**
     * Adresse de l'appelant, clé des quotas. {@code getRemoteAddr()} suffit :
     * {@code server.forward-headers-strategy=framework} (application.properties) fait réécrire la
     * requête par le {@code ForwardedHeaderFilter} de Spring, qui y reporte le {@code X-Forwarded-For}
     * du proxy TLS.
     * <p>
     * ⚠️ En production, le reverse proxy <strong>doit écraser</strong> cet en-tête au lieu de le
     * compléter : sinon un client le forge à chaque requête et se donne une adresse neuve — donc un
     * quota neuf — à volonté.
     */
    private static String adresse(HttpServletRequest requete) {
        String ip = requete.getRemoteAddr();
        return ip == null || ip.isBlank() ? "adresse-inconnue" : ip;
    }
}
