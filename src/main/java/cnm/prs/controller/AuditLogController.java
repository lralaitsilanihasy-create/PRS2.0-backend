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

import cnm.prs.dto.AuditLogDto;
import cnm.prs.service.AuditLogService;

/**
 * Contrôleur REST pour la ressource {@code audit-logs} (table {@code t_audit_log}).
 * Journal d'audit réservé à l'Administrateur (§3.8) ; <strong>lecture seule</strong> —
 * création, modification et suppression répondent 409 (⚠️ audit 2026-08-27 : le journal est en
 * ajout seul, alimenté par le seul intercepteur d'écriture, et l'Administrateur lui-même n'y écrit pas).
 */
@RestController
@RequestMapping("/api/audit-logs")
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class AuditLogController {

    private final AuditLogService service;

    public AuditLogController(AuditLogService service) {
        this.service = service;
    }

    @GetMapping
    public List<AuditLogDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public AuditLogDto findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<AuditLogDto> create(@Valid @RequestBody AuditLogDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/{id}")
    public AuditLogDto update(@PathVariable Long id, @Valid @RequestBody AuditLogDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
