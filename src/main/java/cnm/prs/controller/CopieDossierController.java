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

import cnm.prs.dto.CopieDossierDto;
import cnm.prs.service.CopieDossierService;

/**
 * Contrôleur REST pour la ressource {@code copie-dossiers} (table {@code t_copie_dossier}).
 *
 * <p>La copie de dossier est une pièce du <strong>circuit interne</strong> (§3.3,
 * {@code TYPE_COPIE = DISPATCH_CC}) : la lecture est scopée à la localité du dossier (dans
 * {@link cnm.prs.service.CopieDossierService}). L'écriture est réservée à l'{@code ADMINISTRATEUR} —
 * aucun écran ne la consomme et le circuit ne produit pas encore ces copies ; un accusé de réception
 * ({@code ACCUSE_RECEPTION}, {@code DATE_ACCUSE}) posable par n'importe quel porteur de jeton
 * attesterait une transmission qui n'a pas eu lieu.</p>
 */
@RestController
@RequestMapping("/api/copie-dossiers")
public class CopieDossierController {

    private final CopieDossierService service;

    public CopieDossierController(CopieDossierService service) {
        this.service = service;
    }

    @GetMapping
    public List<CopieDossierDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public CopieDossierDto findById(@PathVariable Integer id) {
        return service.findById(id);
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PostMapping
    public ResponseEntity<CopieDossierDto> create(@Valid @RequestBody CopieDossierDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PutMapping("/{id}")
    public CopieDossierDto update(@PathVariable Integer id, @Valid @RequestBody CopieDossierDto dto) {
        return service.update(id, dto);
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
