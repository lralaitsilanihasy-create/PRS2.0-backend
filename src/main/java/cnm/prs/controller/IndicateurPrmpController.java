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

import cnm.prs.dto.IndicateurPrmpDto;
import cnm.prs.service.IndicateurPrmpService;

/**
 * Contrôleur REST pour la ressource {@code indicateur-prmps} (table {@code t_indicateur_prmp}).
 *
 * <p>Périmètre de <strong>propriété</strong> (dans {@link cnm.prs.service.IndicateurPrmpService}) :
 * Président/Administrateur voient tout, la PRMP (et l'UGPM de sa tutelle) ne voit que les siens, tout
 * autre profil ne voit rien. L'écriture est réservée à l'{@code ADMINISTRATEUR} — le bilan annuel d'une
 * PRMP ne se corrige pas par la PRMP qu'il évalue.</p>
 */
@RestController
@RequestMapping("/api/indicateur-prmps")
public class IndicateurPrmpController {

    private final IndicateurPrmpService service;

    public IndicateurPrmpController(IndicateurPrmpService service) {
        this.service = service;
    }

    @GetMapping
    public List<IndicateurPrmpDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public IndicateurPrmpDto findById(@PathVariable Integer id) {
        return service.findById(id);
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PostMapping
    public ResponseEntity<IndicateurPrmpDto> create(@Valid @RequestBody IndicateurPrmpDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PutMapping("/{id}")
    public IndicateurPrmpDto update(@PathVariable Integer id, @Valid @RequestBody IndicateurPrmpDto dto) {
        return service.update(id, dto);
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
