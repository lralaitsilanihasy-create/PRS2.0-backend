package cnm.prs.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.ActeGestionDto;
import cnm.prs.service.ActesGestionService;

/**
 * ⚠️ 2026-10-09 (manuel de contrôle a priori, tranche M5a, §B1 et §B5 ; V98) — les actes de gestion contractuelle déposés depuis le
 * marché : {@code /api/dossiers/{idMarche}/actes-gestion} (le marché et ses actes ; dépôt) et {@code /api/actes-gestion/{idDossier}}
 * (l'acte d'un dossier DGC ; modification en brouillon).
 */
@RestController
public class ActeGestionController {

    private final ActesGestionService service;

    public ActeGestionController(ActesGestionService service) {
        this.service = service;
    }

    @GetMapping("/api/dossiers/{idMarche}/actes-gestion")
    public ActeGestionDto.Marche marche(@PathVariable Integer idMarche) {
        return service.marche(idMarche);
    }

    @PostMapping("/api/dossiers/{idMarche}/actes-gestion")
    public ResponseEntity<ActeGestionDto.Acte> deposer(@PathVariable Integer idMarche, @RequestBody ActeGestionDto.Demande demande) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.deposer(idMarche, demande));
    }

    @GetMapping("/api/actes-gestion/{idDossier}")
    public ActeGestionDto.Acte acte(@PathVariable Integer idDossier) {
        return service.acte(idDossier);
    }

    @PutMapping("/api/actes-gestion/{idDossier}")
    public ActeGestionDto.Acte modifier(@PathVariable Integer idDossier, @RequestBody ActeGestionDto.Demande demande) {
        return service.modifier(idDossier, demande);
    }
}
