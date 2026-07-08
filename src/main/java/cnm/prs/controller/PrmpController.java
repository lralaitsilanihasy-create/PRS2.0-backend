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
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import cnm.prs.dto.PrmpDto;
import cnm.prs.dto.SuppressionLotPrmpRequest;
import cnm.prs.dto.SuppressionLotPrmpResult;
import cnm.prs.service.PrmpService;

/**
 * Contrôleur REST pour la ressource {@code prmps} (table {@code t_prmp}).
 */
@RestController
@RequestMapping("/api/prmps")
public class PrmpController {

    private final PrmpService service;

    public PrmpController(PrmpService service) {
        this.service = service;
    }

    @GetMapping
    public List<PrmpDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public PrmpDto findById(@PathVariable String id) {
        return service.findById(id);
    }

    /** PRMP rattachées à une localité via leurs entités contractantes actives (liste, vide si aucune). */
    @GetMapping("/par-localite/{idLocalite}")
    public List<PrmpDto> findByLocalite(@PathVariable String idLocalite) {
        return service.findByLocalite(idLocalite);
    }

    /** PRMP rattachée à une entité contractante (affectation active) : 0 ou 1, en liste (vide si aucune). */
    @GetMapping("/par-entite/{idEntiteContract}")
    public List<PrmpDto> findByEntite(@PathVariable Integer idEntiteContract) {
        return service.findByEntite(idEntiteContract);
    }

    @PostMapping
    public ResponseEntity<PrmpDto> create(@Valid @RequestBody PrmpDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/{id}")
    public PrmpDto update(@PathVariable String id, @Valid @RequestBody PrmpDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Suppression en lot (tolérante) : bilan {@code {supprimes[], introuvables[], bloques[]}}. Réservé
     * {@code ADMINISTRATEUR} (ce sous-chemin n'est pas couvert par les règles URL de SecurityConfig).
     */
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PostMapping("/suppression-lot")
    public SuppressionLotPrmpResult supprimerLot(@Valid @RequestBody SuppressionLotPrmpRequest req) {
        return service.supprimerLot(req.matricules());
    }
}
