package cnm.prs.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    /**
     * ⚠️ Audit 2026-08-27 (lot D §4 — <strong>ajout de contrat</strong>) — journal PAGINÉ et FILTRÉ :
     * {@code ?page=&size=} (routage Spring sur la présence de {@code page}, motif des autres
     * ressources), tri {@code dateAction} décroissant imposé par le serveur, et quatre filtres
     * facultatifs — {@code table} (nom de table auditée, exact), {@code acteur} ({@code IM_ACTEUR},
     * exact), {@code du} et {@code au} (dates {@code AAAA-MM-JJ}, bornes incluses).
     *
     * <p>Réservé à l'<strong>Administrateur</strong>, comme toute la ressource. Sans {@code page},
     * la liste plate ci-dessous reste servie (rétro-compatible), désormais plafonnée.</p>
     */
    @GetMapping(params = "page")
    public Page<AuditLogDto> rechercher(
            @RequestParam(name = "table", required = false) String nomTable,
            @RequestParam(required = false) String acteur,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            Pageable pageable) {
        return service.rechercher(nomTable, acteur, du, au, pageable);
    }

    /**
     * Liste historique, <strong>plafonnée aux 500 entrées les plus récentes</strong> (⚠️ audit
     * 2026-08-27, lot D §4) : {@code t_audit_log} croît d'une ligne à chaque écriture de
     * l'application, sans fin. Pour remonter au-delà, ou filtrer, passer par la variante paginée.
     */
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
