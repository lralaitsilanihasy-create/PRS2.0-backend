package cnm.prs.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import cnm.prs.dto.CopieDossierDto;
import cnm.prs.service.CopieDossierService;

/**
 * Contrôleur REST pour la ressource {@code copie-dossiers} (table {@code t_copie_dossier}).
 *
 * <p>⚠️ LOT 3a (2026-08-26) — §1 : pièce <strong>interne</strong> du circuit, créée par
 * {@code DispatchService}. <strong>Lecture</strong> scopée dans le service à la localité du contrôleur
 * (Président/Administrateur : tout ; PRMP : rien). <strong>Écriture</strong> générique réservée à
 * l'Administrateur — les vraies copies naissent du dispatch, pas d'un POST.</p>
 */
@RestController
@RequestMapping("/api/copie-dossiers")
public class CopieDossierController {

    private final CopieDossierService service;

    public CopieDossierController(CopieDossierService service) {
        this.service = service;
    }

    @GetMapping
    public List<CopieDossierDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public CopieDossierDto findById(@PathVariable Integer id) {
        return service.findById(id);
    }

    /** ⚠️ LOT 3a (2026-08-26) — §1 : écriture générique réservée à l'Administrateur (voir en-tête). */
    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public ResponseEntity<CopieDossierDto> create(@Valid @RequestBody CopieDossierDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    /** ⚠️ LOT 3a (2026-08-26) — Administrateur seul (voir création). */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public CopieDossierDto update(@PathVariable Integer id, @Valid @RequestBody CopieDossierDto dto) {
        return service.update(id, dto);
    }

    /** ⚠️ LOT 3a (2026-08-26) — Administrateur seul (voir création). */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
