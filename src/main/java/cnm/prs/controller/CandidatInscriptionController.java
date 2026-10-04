package cnm.prs.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.CompteCandidatDto;
import cnm.prs.service.CandidatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 1a, §B2) — les routes <strong>publiques</strong> (sans
 * session) du compte candidat : inscription, confirmation par codes, renvoi des codes. La connexion passe par
 * {@code POST /api/auth/login}, avec l'adresse électronique pour login. Ouvertes dans {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/candidats")
public class CandidatInscriptionController {

    private final CandidatService service;

    public CandidatInscriptionController(CandidatService service) {
        this.service = service;
    }

    /** 201 {@code { idCompte, etat: A_CONFIRMER }} ; 400 ; 409 {@code EMAIL_EXISTANT} ; 429. */
    @PostMapping("/inscription")
    public ResponseEntity<CompteCandidatDto.Inscrit> inscrire(@Valid @RequestBody CompteCandidatDto.Inscription corps,
            HttpServletRequest requete) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.inscrire(corps, adresse(requete)));
    }

    /** 200 {@code { etat: CONFIRME }} ; 400 {@code CODE_INVALIDE} / {@code CODE_EXPIRE} ; 404 ; 429. */
    @PostMapping("/confirmation")
    public CompteCandidatDto.Etat confirmer(@Valid @RequestBody CompteCandidatDto.Confirmation corps) {
        return service.confirmer(corps);
    }

    /** 204 dans tous les cas (sans dire si l'adresse est connue) ; 429. */
    @PostMapping("/codes")
    public ResponseEntity<Void> renvoyerCodes(@Valid @RequestBody CompteCandidatDto.RenvoiCodes corps) {
        service.renvoyerCodes(corps);
        return ResponseEntity.noContent().build();
    }

    private static String adresse(HttpServletRequest requete) {
        String ip = requete.getRemoteAddr();
        return ip == null || ip.isBlank() ? "adresse-inconnue" : ip;
    }
}
