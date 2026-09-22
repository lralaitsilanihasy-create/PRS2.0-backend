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

import cnm.prs.dto.ExamenDto;
import cnm.prs.dto.ExamenSoumissionRequest;
import cnm.prs.service.ExamenService;

/**
 * Contrôleur REST pour la ressource {@code examens} (table {@code t_examen}).
 */
@RestController
@RequestMapping("/api/examens")
public class ExamenController {

    private final ExamenService service;

    public ExamenController(ExamenService service) {
        this.service = service;
    }

    @GetMapping
    public List<ExamenDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ExamenDto findById(@PathVariable Integer id) {
        return service.findById(id);
    }

    // Examen point par point : Membre, CC ou Président (§2.4, §3.5).
    @PreAuthorize("@perm.peutExercer('MEMBRE')")
    @PostMapping
    public ResponseEntity<ExamenDto> create(@Valid @RequestBody ExamenDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PreAuthorize("@perm.peutExercer('MEMBRE')")
    @PutMapping("/{id}")
    public ExamenDto update(@PathVariable Integer id, @Valid @RequestBody ExamenDto dto) {
        return service.update(id, dto);
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * ⚠️ Règle ajoutée — soumission de l'examen : le Membre choisit le résultat (Projet de PV ou
     * lettre de renvoi). Renvoie le {@code PvExamenDto} ou le {@code LettreRenvoiDto} créé.
     */
    @PreAuthorize("@perm.peutExercer('MEMBRE')")
    @PostMapping("/{id}/soumettre")
    public ResponseEntity<Object> soumettre(@PathVariable Integer id,
            @Valid @RequestBody ExamenSoumissionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.soumettre(id, req));
    }

    /**
     * ⚠️ Règle ajoutée (demande front du 2026-09-21) — <strong>réinitialisation</strong> d'un examen en cours par
     * son <strong>attributaire</strong> : tous les points, observations et pièces effacés d'un geste, la ligne
     * d'examen conservée, le chronométrage intact. Sans corps. Réservé au brouillon jamais soumis (dossier
     * {@code DISPATCHE}, aucun projet de PV) — 409 sinon ; 403 nominatif pour tout autre que l'attributaire.
     */
    @PreAuthorize("@perm.peutExercer('MEMBRE')")
    @PostMapping("/{id}/reinitialiser")
    public ExamenDto reinitialiser(@PathVariable Integer id) {
        return service.reinitialiser(id);
    }
}
