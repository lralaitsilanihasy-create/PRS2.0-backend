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

import cnm.prs.dto.TrancheDto;
import cnm.prs.service.TrancheService;

/**
 * Contrôleur REST pour la ressource {@code tranches} (table {@code t_tranche}).
 *
 * <p>⚠️ LOT 3a (2026-08-26) — §1/§3.1, même politique que {@code LotController} : lecture scopée dans
 * le service au périmètre du dossier parent (atteint via le lot porteur), écriture réservée à la
 * PRMP/UGPM propriétaire d'un dossier en {@code BROUILLON}, ou à l'Administrateur.</p>
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

    /** ⚠️ LOT 3a (2026-08-26) — §3.1 : écriture réservée à la PRMP/UGPM propriétaire d'un brouillon (ou Admin). */
    @PostMapping
    @PreAuthorize("hasAnyRole('PRMP','UGPM','ADMINISTRATEUR')")
    public ResponseEntity<TrancheDto> create(@Valid @RequestBody TrancheDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    /** ⚠️ LOT 3a (2026-08-26) — §3.1 : idem création. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PRMP','UGPM','ADMINISTRATEUR')")
    public TrancheDto update(@PathVariable Integer id, @Valid @RequestBody TrancheDto dto) {
        return service.update(id, dto);
    }

    /** ⚠️ LOT 3a (2026-08-26) — §3.1 : idem création. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('PRMP','UGPM','ADMINISTRATEUR')")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
