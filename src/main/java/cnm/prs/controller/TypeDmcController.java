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

import cnm.prs.dto.TypeDmcDto;
import cnm.prs.service.TypeDmcService;

/**
 * Contrôleur REST pour la ressource {@code type-dmc} (table {@code t_type_dmc}) : référentiel des
 * types de dossier de mise en concurrence. Lecture ouverte aux utilisateurs authentifiés ; écriture
 * réservée à l'Administrateur (cf. {@code SecurityConfig.REFERENTIELS}).
 */
@RestController
@RequestMapping("/api/type-dmc")
public class TypeDmcController {

    private final TypeDmcService service;

    public TypeDmcController(TypeDmcService service) {
        this.service = service;
    }

    @GetMapping
    public List<TypeDmcDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public TypeDmcDto findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<TypeDmcDto> create(@Valid @RequestBody TypeDmcDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/{id}")
    public TypeDmcDto update(@PathVariable Long id, @Valid @RequestBody TypeDmcDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
