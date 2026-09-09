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

import cnm.prs.dto.StatutMarcheDto;
import cnm.prs.service.StatutMarcheService;

/**
 * ⚠️ Contrôleur REST du référentiel « Statut de marché » (table {@code tr_statut_marche}, demande du
 * 2026-09-09).
 *
 * <p>Comme les autres référentiels, la <strong>lecture est ouverte aux profils authentifiés</strong> (les
 * listes déroulantes de saisie en dépendent) et l'<strong>écriture est réservée à l'Administrateur</strong>.
 * La garde n'est pas ici mais dans {@code SecurityConfig}, qui la tient par URL pour toute la famille :
 * une règle par contrôleur se serait désalignée du reste au premier oubli.</p>
 */
@RestController
@RequestMapping("/api/statut-marches")
public class StatutMarcheController {

    private final StatutMarcheService service;

    public StatutMarcheController(StatutMarcheService service) {
        this.service = service;
    }

    @GetMapping
    public List<StatutMarcheDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{code}")
    public StatutMarcheDto findById(@PathVariable String code) {
        return service.findById(code);
    }

    @PostMapping
    public ResponseEntity<StatutMarcheDto> create(@Valid @RequestBody StatutMarcheDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/{code}")
    public StatutMarcheDto update(@PathVariable String code, @Valid @RequestBody StatutMarcheDto dto) {
        return service.update(code, dto);
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@PathVariable String code) {
        service.delete(code);
        return ResponseEntity.noContent().build();
    }
}
