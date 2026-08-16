package cnm.prs.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import jakarta.validation.Valid;

import cnm.prs.dto.PpmDto;
import cnm.prs.service.PpmService;

/**
 * Contrôleur REST pour la ressource {@code ppms} (table {@code t_ppm}).
 */
@RestController
@RequestMapping("/api/ppms")
public class PpmController {

    private final PpmService service;

    public PpmController(PpmService service) {
        this.service = service;
    }

    /** ⚠️ Audit front (2026-08-16) — même liste, paginée ({@code ?page=&size=}) ; sans {@code page}, liste plate. */
    @GetMapping(params = "page")
    public org.springframework.data.domain.Page<PpmDto> findAllPagine(
            org.springframework.data.domain.Pageable pageable) {
        return service.findAllPagine(pageable);
    }

    @GetMapping
    public List<PpmDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public PpmDto findById(@PathVariable Integer id) {
        return service.findById(id);
    }

    // Création brute réservée Admin ; la saisie passe par /api/saisies/ppm (PRMP).
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PostMapping
    public ResponseEntity<PpmDto> create(@Valid @RequestBody PpmDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    // Édition de l'en-tête PPM d'un brouillon : PRMP (propriétaire) ou Admin ; validé en service.
    @PreAuthorize("hasAnyRole('PRMP','UGPM','ADMINISTRATEUR')")
    @PutMapping("/{id}")
    public PpmDto update(@PathVariable Integer id, @Valid @RequestBody PpmDto dto) {
        return service.update(id, dto);
    }

    // Édition restreinte (rectification) : PRMP propriétaire, uniquement si dossier EN_ATTENTE_DECISION_PRMP.
    // Corps SANS validation des champs d'identité figés (idDossier/idPrmp/idLocalite), que le front n'envoie
    // pas en rectification ; le contenu est appliqué, l'identité conservée serveur.
    @PreAuthorize("hasAnyRole('PRMP','UGPM')")
    @PatchMapping("/{id}/rectifier")
    public PpmDto rectifier(@PathVariable Integer id, @RequestBody PpmDto dto) {
        return service.modifierEnAttenteRectification(id, dto);
    }

    // Suppression d'un PPM de brouillon : PRMP propriétaire (miroir du marché) ; garde BROUILLON+propriété en service.
    @PreAuthorize("hasAnyRole('PRMP','UGPM')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
