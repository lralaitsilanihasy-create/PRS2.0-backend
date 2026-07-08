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

import cnm.prs.dto.ControleurDto;
import cnm.prs.dto.SuppressionLotControleurRequest;
import cnm.prs.dto.SuppressionLotControleurResult;
import cnm.prs.service.ControleurService;

/**
 * Contrôleur REST pour la ressource {@code controleurs} (table {@code tr_controleur}).
 */
@RestController
@RequestMapping("/api/controleurs")
public class ControleurController {

    private final ControleurService service;

    public ControleurController(ControleurService service) {
        this.service = service;
    }

    @GetMapping
    public List<ControleurDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ControleurDto findById(@PathVariable String id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<ControleurDto> create(@Valid @RequestBody ControleurDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/{id}")
    public ControleurDto update(@PathVariable String id, @Valid @RequestBody ControleurDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Suppression en lot (tolérante) : bilan {@code {supprimes[], introuvables[], bloques[]}}. Réservé
     * {@code ADMINISTRATEUR} (ce sous-chemin n'est pas couvert par les règles URL de SecurityConfig).
     */
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PostMapping("/suppression-lot")
    public SuppressionLotControleurResult supprimerLot(@Valid @RequestBody SuppressionLotControleurRequest req) {
        return service.supprimerLot(req.matricules());
    }
}
