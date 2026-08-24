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
 * Journal d'audit réservé à l'Administrateur (§3.8).
 *
 * <p><strong>Ressource en lecture seule.</strong> Le journal est alimenté par le serveur lui-même
 * ({@code AuditInterceptor} et les services métier, via {@code AuditLogService#enregistrer}).
 * Les trois verbes d'écriture restent routés pour porter un refus <strong>explicite</strong> —
 * 409 « journal immuable », comme le {@code DELETE} le faisait déjà (S5) : un appelant apprend
 * ainsi <em>pourquoi</em> l'écriture est refusée, là où un 405 ne dirait que « mauvais verbe ».</p>
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

    /** Routé pour refuser explicitement : le journal ne se forge pas (409, §3.8). */
    @PostMapping
    public ResponseEntity<AuditLogDto> create(@Valid @RequestBody AuditLogDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    /** Routé pour refuser explicitement : le journal ne se réécrit pas (409, §3.8). */
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
