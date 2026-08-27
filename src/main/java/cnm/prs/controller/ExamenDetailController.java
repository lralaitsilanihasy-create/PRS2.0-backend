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
import org.springframework.security.access.prepost.PreAuthorize;

import jakarta.validation.Valid;

import cnm.prs.dto.ExamenDetailDto;
import cnm.prs.service.ExamenDetailService;

/**
 * Contrôleur REST pour la ressource {@code examen-details} (table {@code t_examen_detail}).
 *
 * <p><strong>Lecture</strong> (⚠️ audit 2026-08-27, C2) : bornée au périmètre dans le service —
 * Président/Administrateur voient tout, les contrôleurs leur localité, la PRMP/UGPM rien
 * (liste vide, 403 sur l'accès unitaire) : l'évaluation point par point est interne à la commission.</p>
 */
@RestController
@RequestMapping("/api/examen-details")
public class ExamenDetailController {

    private final ExamenDetailService service;

    public ExamenDetailController(ExamenDetailService service) {
        this.service = service;
    }

    @GetMapping
    public List<ExamenDetailDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ExamenDetailDto findById(@PathVariable Integer id) {
        return service.findById(id);
    }

    // Détail d'examen (points de contrôle) : Membre, CC ou Président (§3.5).
    @PreAuthorize("@perm.peutExercer('MEMBRE')")
    @PostMapping
    public ResponseEntity<ExamenDetailDto> create(@Valid @RequestBody ExamenDetailDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PreAuthorize("@perm.peutExercer('MEMBRE')")
    @PutMapping("/{id}")
    public ExamenDetailDto update(@PathVariable Integer id, @Valid @RequestBody ExamenDetailDto dto) {
        return service.update(id, dto);
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
