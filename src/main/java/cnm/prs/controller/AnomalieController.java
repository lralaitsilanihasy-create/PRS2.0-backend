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

import cnm.prs.dto.AnomalieDto;
import cnm.prs.service.AnomalieService;

/**
 * Contrôleur REST pour la ressource {@code anomalies} (table {@code t_anomalie}).
 *
 * <p>⚠️ LOT 3a (2026-08-26) — §3.1 « Aucun accès au journal d'audit, aux anomalies ni aux
 * statistiques CNM globales » (PRMP) et §3.5 (le Membre non plus). La ressource était lisible et
 * modifiable par tout authentifié. <strong>Lecture</strong> : Président et Administrateur.
 * <strong>Écriture</strong> : Administrateur seul (les anomalies sont détectées par les règles de
 * {@code tr_regle_anomalie}, pas saisies).</p>
 */
@RestController
@RequestMapping("/api/anomalies")
@PreAuthorize("hasAnyRole('PRESIDENT','ADMINISTRATEUR')")
public class AnomalieController {

    private final AnomalieService service;

    public AnomalieController(AnomalieService service) {
        this.service = service;
    }

    @GetMapping
    public List<AnomalieDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public AnomalieDto findById(@PathVariable Integer id) {
        return service.findById(id);
    }

    /** ⚠️ LOT 3a (2026-08-26) — écriture Administrateur seul (restreint la règle de classe). */
    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public ResponseEntity<AnomalieDto> create(@Valid @RequestBody AnomalieDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    /** ⚠️ LOT 3a (2026-08-26) — Administrateur seul (voir création). */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public AnomalieDto update(@PathVariable Integer id, @Valid @RequestBody AnomalieDto dto) {
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
