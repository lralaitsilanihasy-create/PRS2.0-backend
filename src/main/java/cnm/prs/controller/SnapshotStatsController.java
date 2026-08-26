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

import cnm.prs.dto.SnapshotStatsDto;
import cnm.prs.service.SnapshotStatsService;

/**
 * Contrôleur REST pour la ressource {@code snapshot-statss} (table {@code t_snapshot_stats}).
 *
 * <p>⚠️ LOT 3a (2026-08-26) — §3.2 (KPIs agrégés toutes localités) et §3.1 (« aucun accès aux
 * statistiques CNM globales » pour la PRMP). <strong>Lecture</strong> : Président et Administrateur —
 * c'est du pilotage global, pas une vue de localité. <strong>Écriture</strong> : Administrateur seul
 * (les instantanés sont alimentés par le système).</p>
 */
@RestController
@RequestMapping("/api/snapshot-statss")
@PreAuthorize("hasAnyRole('PRESIDENT','ADMINISTRATEUR')")
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

    /** ⚠️ LOT 3a (2026-08-26) — écriture Administrateur seul (restreint la règle de classe). */
    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public ResponseEntity<SnapshotStatsDto> create(@Valid @RequestBody SnapshotStatsDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    /** ⚠️ LOT 3a (2026-08-26) — Administrateur seul (voir création). */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public SnapshotStatsDto update(@PathVariable Integer id, @Valid @RequestBody SnapshotStatsDto dto) {
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
