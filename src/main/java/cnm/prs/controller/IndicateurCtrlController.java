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

import cnm.prs.dto.IndicateurCtrlDto;
import cnm.prs.service.IndicateurCtrlService;

/**
 * Contrôleur REST pour la ressource {@code indicateur-ctrls} (table {@code t_indicateur_ctrl}).
 *
 * <p>⚠️ LOT 3a (2026-08-26) — §3.2 : performance <strong>nominative</strong> des contrôleurs (nombre
 * d'examens, délai moyen, observations émises). Lecture ouverte à tout authentifié jusqu'ici : elle
 * est désormais réservée au Président et à l'Administrateur — c'est un instrument de pilotage
 * hiérarchique, pas une donnée de travail. Écriture : Administrateur seul (alimentation système).</p>
 */
@RestController
@RequestMapping("/api/indicateur-ctrls")
@PreAuthorize("hasAnyRole('PRESIDENT','ADMINISTRATEUR')")
public class IndicateurCtrlController {

    private final IndicateurCtrlService service;

    public IndicateurCtrlController(IndicateurCtrlService service) {
        this.service = service;
    }

    @GetMapping
    public List<IndicateurCtrlDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public IndicateurCtrlDto findById(@PathVariable Integer id) {
        return service.findById(id);
    }

    /** ⚠️ LOT 3a (2026-08-26) — écriture Administrateur seul (restreint la règle de classe). */
    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public ResponseEntity<IndicateurCtrlDto> create(@Valid @RequestBody IndicateurCtrlDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    /** ⚠️ LOT 3a (2026-08-26) — Administrateur seul (voir création). */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public IndicateurCtrlDto update(@PathVariable Integer id, @Valid @RequestBody IndicateurCtrlDto dto) {
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
