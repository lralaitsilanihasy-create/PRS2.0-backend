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

import cnm.prs.dto.CategorieEntiteDto;
import cnm.prs.service.CategorieEntiteService;

/**
 * ⚠️ Référentiel ajouté (2026-07-26) — contrôleur REST pour {@code categorie-entites}
 * (table {@code tr_categorie_entite}). {@code {id}} = libellé (PK texte). Écriture réservée à
 * l'ADMINISTRATEUR (SecurityConfig), lecture ouverte aux authentifiés — comme les autres référentiels.
 */
@RestController
@RequestMapping("/api/categorie-entites")
public class CategorieEntiteController {

    private final CategorieEntiteService service;

    public CategorieEntiteController(CategorieEntiteService service) {
        this.service = service;
    }

    @GetMapping
    public List<CategorieEntiteDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public CategorieEntiteDto findById(@PathVariable String id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<CategorieEntiteDto> create(@Valid @RequestBody CategorieEntiteDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/{id}")
    public CategorieEntiteDto update(@PathVariable String id, @Valid @RequestBody CategorieEntiteDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
