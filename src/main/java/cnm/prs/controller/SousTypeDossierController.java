package cnm.prs.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import cnm.prs.dto.SousTypeDossierDto;
import cnm.prs.service.SousTypeDossierService;

/**
 * Contrôleur REST pour la ressource {@code sous-type-dossiers} (table {@code tr_sous_type_dossier}).
 * Référentiel administrable : écritures réservées ADMINISTRATEUR (règles d'URL, SecurityConfig),
 * lectures ouvertes — dont la lecture « par famille » consommée par le front à la saisie.
 */
@RestController
@RequestMapping("/api/sous-type-dossiers")
public class SousTypeDossierController {

    private final SousTypeDossierService service;

    public SousTypeDossierController(SousTypeDossierService service) {
        this.service = service;
    }

    @GetMapping
    public List<SousTypeDossierDto> findAll() {
        return service.findAll();
    }

    /** Sous-types d'une famille (ex. {@code GET /par-famille/DDP} → PPM, PPM-AGPM). */
    @GetMapping("/par-famille/{idTypeDossier}")
    public List<SousTypeDossierDto> findParFamille(@PathVariable String idTypeDossier) {
        return service.findParFamille(idTypeDossier);
    }

    @GetMapping("/{id}")
    public SousTypeDossierDto findById(@PathVariable String id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<SousTypeDossierDto> create(@Valid @RequestBody SousTypeDossierDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/{id}")
    public SousTypeDossierDto update(@PathVariable String id, @Valid @RequestBody SousTypeDossierDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
