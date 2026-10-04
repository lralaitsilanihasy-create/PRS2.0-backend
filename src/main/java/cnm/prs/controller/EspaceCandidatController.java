package cnm.prs.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.EntrepriseCandidatDto;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.security.CurrentUser;
import cnm.prs.service.EntrepriseCandidatService;
import jakarta.validation.Valid;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 1b, §B3) — l'<strong>espace candidat</strong>
 * ({@code /api/candidat/**}, profil {@code CANDIDAT} seul, garde de {@code SecurityConfig}) : son entreprise et ses
 * pièces. Le candidat est celui du jeton ({@code ref}) : aucune route ne prend un identifiant de compte.
 */
@RestController
@RequestMapping("/api/candidat")
@PreAuthorize("hasRole('CANDIDAT')")
public class EspaceCandidatController {

    private final EntrepriseCandidatService service;

    public EspaceCandidatController(EntrepriseCandidatService service) {
        this.service = service;
    }

    /** {@code EntrepriseDto} ; 404 si l'entreprise n'est pas encore déclarée. */
    @GetMapping("/entreprise")
    public EntrepriseCandidatDto.Entreprise entreprise() {
        return service.lire(moi());
    }

    /** Déclaration ou mise à jour ; 400 ; 409 {@code NIF_EXISTANT} / {@code STAT_EXISTANT} / {@code RCS_EXISTANT}. */
    @PutMapping("/entreprise")
    public EntrepriseCandidatDto.Entreprise enregistrer(@Valid @RequestBody EntrepriseCandidatDto.Saisie corps) {
        return service.enregistrer(moi(), corps);
    }

    /** 201 ; 400 (type, format) ; 409 {@code ENTREPRISE_ABSENTE} ; 413. */
    @PostMapping(value = "/entreprise/pieces", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EntrepriseCandidatDto.Piece> ajouterPiece(@RequestParam("type") String type,
            @RequestPart("fichier") MultipartFile fichier) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.ajouterPiece(moi(), type, fichier));
    }

    @DeleteMapping("/entreprise/pieces/{id}")
    public ResponseEntity<Void> supprimerPiece(@PathVariable Integer id) {
        service.supprimerPiece(moi(), id);
        return ResponseEntity.noContent().build();
    }

    private static String moi() {
        return CurrentUser.ref().orElseThrow(() -> new ResourceNotFoundException("Compte candidat introuvable."));
    }
}
