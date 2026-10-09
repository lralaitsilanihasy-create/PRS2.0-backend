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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import cnm.prs.dto.MotifTypeDto;
import cnm.prs.service.MotifsTypesService;

/**
 * ⚠️ 2026-10-09 (manuel de contrôle a priori, M4, §B4 ; V97) — les motifs-types de renvoi et d'avis défavorable : référentiel
 * {@code /api/motifs-types} (écriture réservée à l'Administrateur, lecture ouverte), et les motifs d'un dossier
 * {@code /api/dossiers/{id}/motifs-types} (même garde de lecture que le dossier).
 */
@RestController
public class MotifTypeController {

    private final MotifsTypesService service;

    public MotifTypeController(MotifsTypesService service) {
        this.service = service;
    }

    @GetMapping("/api/motifs-types")
    public List<MotifTypeDto> findAll(@RequestParam(required = false) String typeDossier,
            @RequestParam(required = false) String sousType, @RequestParam(required = false) String nature) {
        return service.findAll(typeDossier, sousType, nature);
    }

    @GetMapping("/api/motifs-types/{id}")
    public MotifTypeDto findById(@PathVariable Integer id) {
        return service.findById(id);
    }

    @PostMapping("/api/motifs-types")
    public ResponseEntity<MotifTypeDto> create(@Valid @RequestBody MotifTypeDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/api/motifs-types/{id}")
    public MotifTypeDto update(@PathVariable Integer id, @Valid @RequestBody MotifTypeDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/api/motifs-types/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/dossiers/{id}/motifs-types")
    public List<MotifTypeDto> duDossier(@PathVariable Integer id, @RequestParam(required = false) String nature) {
        return service.pourDossier(id, nature);
    }
}
