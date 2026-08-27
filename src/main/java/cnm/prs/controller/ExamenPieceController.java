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

import cnm.prs.dto.ExamenPieceDto;
import cnm.prs.service.ExamenPieceService;

/**
 * ⚠️ Règle ajoutée (2026-08-01) — contrôleur REST {@code examen-pieces} (table {@code t_examen_piece}) :
 * examen des pièces jointes une par une. Écriture = tâche du Membre (titulaire ou par délégation, comme
 * {@code examen-details}) ; suppression réservée à l'Administrateur.
 *
 * <p><strong>Lecture</strong> (⚠️ audit 2026-08-27, C2) : bornée au périmètre dans le service, avec ou
 * sans {@code ?examen=} — contrôleurs de la localité seulement ; la PRMP/UGPM n'y accède pas.</p>
 */
@RestController
@RequestMapping("/api/examen-pieces")
public class ExamenPieceController {

    private final ExamenPieceService service;

    public ExamenPieceController(ExamenPieceService service) {
        this.service = service;
    }

    @GetMapping
    public List<ExamenPieceDto> findAll(@RequestParam(name = "examen", required = false) Integer examen) {
        return service.findAll(examen);
    }

    @GetMapping("/{id}")
    public ExamenPieceDto findById(@PathVariable Integer id) {
        return service.findById(id);
    }

    @PreAuthorize("@perm.peutExercer('MEMBRE')")
    @PostMapping
    public ResponseEntity<ExamenPieceDto> create(@Valid @RequestBody ExamenPieceDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PreAuthorize("@perm.peutExercer('MEMBRE')")
    @PutMapping("/{id}")
    public ExamenPieceDto update(@PathVariable Integer id, @Valid @RequestBody ExamenPieceDto dto) {
        return service.update(id, dto);
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
