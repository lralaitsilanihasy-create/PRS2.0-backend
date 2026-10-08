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

import cnm.prs.dto.TypePieceJointeDto;
import cnm.prs.service.TypePieceJointeService;

/**
 * Contrôleur REST pour la ressource {@code type-piece-jointes} (table {@code t_type_piece_jointe}).
 * Lecture : authentifié ; écriture (POST/PUT/DELETE) : Administrateur (cf. {@code SecurityConfig.REFERENTIELS}).
 */
@RestController
@RequestMapping("/api/type-piece-jointes")
public class TypePieceJointeController {

    private final TypePieceJointeService service;
    private final cnm.prs.service.PiecesExigees piecesExigees;

    public TypePieceJointeController(TypePieceJointeService service, cnm.prs.service.PiecesExigees piecesExigees) {
        this.piecesExigees = piecesExigees;
        this.service = service;
    }

    /** Tous les types de pièces, ou ceux d'un type de dossier si {@code typeDossier} est fourni. */
    @GetMapping
    public List<TypePieceJointeDto> findAll(@RequestParam(name = "typeDossier", required = false) String typeDossier,
            @RequestParam(name = "sousType", required = false) String sousType) {
        // ⚠️ M2 (manuel de contrôle, §B2) — ?sousType= : la liste du sous-type (obligation, ordre, condition), à défaut celle de sa famille.
        return sousType != null && !sousType.isBlank() ? piecesExigees.duSousType(sousType.trim()) : service.findAll(typeDossier);
    }

    @GetMapping("/{id}")
    public TypePieceJointeDto findById(@PathVariable Integer id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<TypePieceJointeDto> create(@Valid @RequestBody TypePieceJointeDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/{id}")
    public TypePieceJointeDto update(@PathVariable Integer id, @Valid @RequestBody TypePieceJointeDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
