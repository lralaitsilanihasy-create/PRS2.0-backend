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

import jakarta.validation.Valid;

import cnm.prs.dto.LotDto;
import cnm.prs.service.LotService;

/**
 * Contrôleur REST pour la ressource {@code lots} (table {@code t_lot}).
 */
@RestController
@RequestMapping("/api/lots")
public class LotController {

    private final LotService service;

    public LotController(LotService service) {
        this.service = service;
    }

    @GetMapping
    public List<LotDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public LotDto findById(@PathVariable Integer id) {
        return service.findById(id);
    }

    /** Lots d'une ligne de marché (liste, vide si aucun ; pas de 404). */
    @GetMapping("/par-marche/{idDetail}")
    public List<LotDto> findByMarche(@PathVariable Integer idDetail) {
        return service.findByMarche(idDetail);
    }

    @PostMapping
    public ResponseEntity<LotDto> create(@Valid @RequestBody LotDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/{id}")
    public LotDto update(@PathVariable Integer id, @Valid @RequestBody LotDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
