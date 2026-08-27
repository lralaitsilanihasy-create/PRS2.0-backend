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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import cnm.prs.dto.ObservationControleDto;
import cnm.prs.service.ObservationControleService;

/**
 * Contrôleur REST pour la ressource {@code observation-controles} (table {@code t_observation_controle}) :
 * lignes « AU LIEU DE / LIRE » d'un point de contrôle d'examen. Lecture : authentifié <strong>et</strong>
 * bornée au périmètre du point de contrôle (⚠️ audit 2026-08-27, C2 — §1/§3.1 : localité du contrôleur ;
 * la PRMP, acteur externe, n'a pas accès au détail interne) ; écriture (POST/PUT/DELETE) :
 * <strong>Membre</strong> (§3.5, comme les détails d'examen).
 */
@RestController
@RequestMapping("/api/observation-controles")
public class ObservationControleController {

    private final ObservationControleService service;

    public ObservationControleController(ObservationControleService service) {
        this.service = service;
    }

    /** Lignes d'observation d'un point de contrôle (paramètre {@code detail} = idDetail). */
    @GetMapping
    public List<ObservationControleDto> findByDetail(@RequestParam(name = "detail") Integer idDetail) {
        return service.findByDetail(idDetail);
    }

    @PreAuthorize("@perm.peutExercer('MEMBRE')")
    @PostMapping
    public ResponseEntity<ObservationControleDto> create(@Valid @RequestBody ObservationControleDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PreAuthorize("@perm.peutExercer('MEMBRE')")
    @PutMapping("/{id}")
    public ObservationControleDto update(@PathVariable Integer id, @Valid @RequestBody ObservationControleDto dto) {
        return service.update(id, dto);
    }

    @PreAuthorize("@perm.peutExercer('MEMBRE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
