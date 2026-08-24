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

import cnm.prs.dto.TrancheDto;
import cnm.prs.service.TrancheService;

/**
 * Contrôleur REST pour la ressource {@code tranches} (table {@code t_tranche}).
 *
 * <p>Une tranche est une <strong>ressource petite-fille de la ligne de marché</strong> (via son lot) :
 * lectures scopées au périmètre du marché parent (dans {@link TrancheService}), écritures aux mêmes
 * rôles que celles de {@code /api/marches}.</p>
 */
@RestController
@RequestMapping("/api/tranches")
public class TrancheController {

    private final TrancheService service;

    public TrancheController(TrancheService service) {
        this.service = service;
    }

    @GetMapping
    public List<TrancheDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public TrancheDto findById(@PathVariable Integer id) {
        return service.findById(id);
    }

    // Écriture : mêmes rôles que sur la ligne de marché parente (cf. MarcheController) ; périmètre en service.
    @PreAuthorize("hasAnyRole('PRMP','UGPM')")
    @PostMapping
    public ResponseEntity<TrancheDto> create(@Valid @RequestBody TrancheDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PreAuthorize("hasAnyRole('PRMP','UGPM')")
    @PutMapping("/{id}")
    public TrancheDto update(@PathVariable Integer id, @Valid @RequestBody TrancheDto dto) {
        return service.update(id, dto);
    }

    @PreAuthorize("hasAnyRole('PRMP','UGPM')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
