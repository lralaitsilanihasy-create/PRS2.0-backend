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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import cnm.prs.dto.CapmDto;
import cnm.prs.service.CapmService;

/**
 * Contrôleur REST pour la ressource {@code capm} (table {@code t_capm}) : processus de marché.
 * Lecture ouverte aux utilisateurs authentifiés ; écriture réservée à l'Administrateur
 * (cf. {@code SecurityConfig.REFERENTIELS}).
 */
@RestController
@RequestMapping("/api/capm")
public class CapmController {

    private final CapmService service;

    public CapmController(CapmService service) {
        this.service = service;
    }

    // `?mode={idMode}` : grille EFFECTIVE du mode (spécifiques si présents, sinon communs) — ⚠️ règle ajoutée.
    @GetMapping
    public List<CapmDto> findAll(@RequestParam(name = "mode", required = false) Integer mode) {
        return service.findAll(mode);
    }

    @GetMapping("/{id}")
    public CapmDto findById(@PathVariable Integer id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<CapmDto> create(@Valid @RequestBody CapmDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/{id}")
    public CapmDto update(@PathVariable Integer id, @Valid @RequestBody CapmDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
