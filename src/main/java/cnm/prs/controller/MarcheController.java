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

import cnm.prs.dto.MarcheDto;
import cnm.prs.service.MarcheService;

/**
 * Contrôleur REST pour la ressource {@code marches} (table {@code t_marche}).
 */
@RestController
@RequestMapping("/api/marches")
public class MarcheController {

    private final MarcheService service;

    public MarcheController(MarcheService service) {
        this.service = service;
    }

    @GetMapping
    public List<MarcheDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public MarcheDto findById(@PathVariable Integer id) {
        return service.findById(id);
    }

    // Édition des lignes d'un brouillon : réservée à la PRMP (propriétaire) ; validé en service.
    @PreAuthorize("hasAnyRole('PRMP','UGPM')")
    @PostMapping
    public ResponseEntity<MarcheDto> create(@Valid @RequestBody MarcheDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PreAuthorize("hasAnyRole('PRMP','UGPM')")
    @PutMapping("/{id}")
    public MarcheDto update(@PathVariable Integer id, @Valid @RequestBody MarcheDto dto) {
        return service.update(id, dto);
    }

    // Édition restreinte (rectification) : PRMP propriétaire, uniquement si dossier EN_ATTENTE_DECISION_PRMP.
    // Corps SANS validation des champs d'identité figés (idDossier/idPpm), que le front n'envoie pas en
    // rectification ; le contenu est appliqué, l'identité conservée serveur.
    @PreAuthorize("hasAnyRole('PRMP','UGPM')")
    @PatchMapping("/{id}/rectifier")
    public MarcheDto rectifier(@PathVariable Integer id, @RequestBody MarcheDto dto) {
        return service.modifierEnAttenteRectification(id, dto);
    }

    @PreAuthorize("hasAnyRole('PRMP','UGPM')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
