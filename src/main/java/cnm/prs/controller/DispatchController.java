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

import cnm.prs.dto.DispatchDto;
import cnm.prs.service.DispatchService;

/**
 * Contrôleur REST pour la ressource {@code dispatchs} (table {@code t_dispatch}).
 */
@RestController
@RequestMapping("/api/dispatchs")
public class DispatchController {

    private final DispatchService service;

    public DispatchController(DispatchService service) {
        this.service = service;
    }

    @GetMapping
    public List<DispatchDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public DispatchDto findById(@PathVariable Integer id) {
        return service.findById(id);
    }

    // Dispatch : Président (titulaire toutes localités) ou CC (§2.3, §3.3).
    @PreAuthorize("hasAnyRole('PRESIDENT','CHEF_COMMISSION')")
    @PostMapping
    public ResponseEntity<DispatchDto> create(@Valid @RequestBody DispatchDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PreAuthorize("hasAnyRole('PRESIDENT','CHEF_COMMISSION')")
    @PutMapping("/{id}")
    public DispatchDto update(@PathVariable Integer id, @Valid @RequestBody DispatchDto dto) {
        return service.update(id, dto);
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ⚠️ Règle ajoutée — retrait du dossier au Membre : annule le dispatch (dossier → PRET_DISPATCH).
    @PreAuthorize("hasAnyRole('PRESIDENT','CHEF_COMMISSION')")
    @PostMapping("/{id}/annuler")
    public ResponseEntity<Void> annuler(@PathVariable Integer id) {
        service.annuler(id);
        return ResponseEntity.noContent().build();
    }
}
