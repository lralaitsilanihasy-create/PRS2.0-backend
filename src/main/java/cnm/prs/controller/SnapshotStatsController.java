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

import cnm.prs.dto.SnapshotStatsDto;
import cnm.prs.service.SnapshotStatsService;

/**
 * Contrôleur REST pour la ressource {@code snapshot-statss} (table {@code t_snapshot_stats}).
 *
 * <p>Périmètre par <strong>localité</strong>, motif habituel des ressources internes (§1, dans
 * {@link cnm.prs.service.SnapshotStatsService}). L'écriture est réservée à l'{@code ADMINISTRATEUR} :
 * l'instantané est un agrégat calculé, pas une donnée déclarative.</p>
 */
@RestController
@RequestMapping("/api/snapshot-statss")
public class SnapshotStatsController {

    private final SnapshotStatsService service;

    public SnapshotStatsController(SnapshotStatsService service) {
        this.service = service;
    }

    @GetMapping
    public List<SnapshotStatsDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public SnapshotStatsDto findById(@PathVariable Integer id) {
        return service.findById(id);
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PostMapping
    public ResponseEntity<SnapshotStatsDto> create(@Valid @RequestBody SnapshotStatsDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PutMapping("/{id}")
    public SnapshotStatsDto update(@PathVariable Integer id, @Valid @RequestBody SnapshotStatsDto dto) {
        return service.update(id, dto);
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
