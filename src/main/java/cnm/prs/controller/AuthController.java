package cnm.prs.controller;

import java.util.List;

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

    public AuthController(AuthService service, EntiteContractService entiteContractService) {
        this.service = service;
        this.entiteContractService = entiteContractService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return service.login(request);
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
