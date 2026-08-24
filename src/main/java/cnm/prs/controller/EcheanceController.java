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

import cnm.prs.dto.EcheanceDto;
import cnm.prs.service.EcheanceService;

/**
 * Contrôleur REST pour la ressource {@code echeances} (table {@code t_echeance}).
 *
 * <p>Un jalon est une <strong>ressource fille de la ligne de marché</strong> : la lecture est scopée au
 * périmètre du marché parent (dans {@link cnm.prs.service.EcheanceService}). L'écriture est réservée à
 * l'{@code ADMINISTRATEUR} — le calendrier des jalons est alimenté par le suivi automatique
 * ({@code findJalonsAAlerter}, §3.1 Module 04), et le seul écran qui lit cette ressource (calendrier
 * PRMP) est en lecture seule.</p>
 */
@RestController
@RequestMapping("/api/echeances")
public class EcheanceController {

    private final EcheanceService service;

    public EcheanceController(EcheanceService service) {
        this.service = service;
    }

    @GetMapping
    public List<EcheanceDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public EcheanceDto findById(@PathVariable Integer id) {
        return service.findById(id);
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PostMapping
    public ResponseEntity<EcheanceDto> create(@Valid @RequestBody EcheanceDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PutMapping("/{id}")
    public EcheanceDto update(@PathVariable Integer id, @Valid @RequestBody EcheanceDto dto) {
        return service.update(id, dto);
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
