package cnm.prs.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;

import cnm.prs.dto.EntitePubliqueDto;
import cnm.prs.dto.LoginRequest;
import cnm.prs.dto.LoginResponse;
import cnm.prs.dto.PrmpPubliqueDto;
import cnm.prs.dto.RegisterPrmpRequest;
import cnm.prs.dto.RegisterPrmpV2Request;
import cnm.prs.dto.RegisterResponse;
import cnm.prs.dto.RegisterUgpmRequest;
import cnm.prs.security.SessionCookies;
import cnm.prs.service.AuthService;
import cnm.prs.service.EntiteContractService;

/**
 * Authentification : émission de jetons JWT. Endpoint public (cf. SecurityConfig).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService service;
    private final EntiteContractService entiteContractService;
    private final SessionCookies sessionCookies;
    /** Phase 3 du plan cookie : à {@code true}, le jeton ne sort plus dans le corps du login. */
    private final boolean cookieExclusif;

    public AuthController(AuthService service, EntiteContractService entiteContractService,
            SessionCookies sessionCookies,
            @Value("${app.auth.cookie.exclusif:false}") boolean cookieExclusif) {
        this.service = service;
        this.entiteContractService = entiteContractService;
        this.sessionCookies = sessionCookies;
        this.cookieExclusif = cookieExclusif;
    }

    /**
     * Connexion. ⚠️ Plan cookie HttpOnly, phase 1 (2026-08-17) : le JWT est AUSSI posé en cookie de
     * session {@code PRS_SESSION} (HttpOnly, SameSite=Strict — voir {@link SessionCookies}). Tant que
     * {@code app.auth.cookie.exclusif} est {@code false}, le jeton reste dans le corps (transition
     * Bearer, rétro-compatible) ; à {@code true} (phase 3), le corps n'en porte plus.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse reponse = service.login(request);
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
     * Référentiel réduit des PRMP (id + nom), destiné à l'origine au menu « PRMP de tutelle »
     * du formulaire d'inscription UGPM.
     *
     * <p>⚠️ Durcissement (2026-08-24) : cette route <strong>n'est plus publique</strong>. Servie
     * anonymement, elle livrait la liste des comptes de connexion existants alors que
     * {@code POST /api/auth/login} n'est pas limité en débit — énumération de comptes puis
     * martelage. Elle exige désormais le rôle <strong>ADMINISTRATEUR</strong>
     * ({@code SecurityConfig}, règle placée avant le {@code permitAll} de {@code /api/auth/**}) :
     * appel anonyme → <strong>401</strong>, autre profil authentifié → <strong>403</strong>.
     * Aucun écran du front ne la consomme. Les autres routes {@code /api/auth/**} (login, logout,
     * register, entites) restent publiques.</p>
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
    public ResponseEntity<RegisterResponse> registerPrmp(@Valid @RequestBody RegisterPrmpRequest request) {
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
            @RequestPart(value = "photo", required = false) MultipartFile photo) {
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
            @RequestPart(value = "photo", required = false) MultipartFile photo) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.registerUgpm(data, cin, photo));
    }
}
