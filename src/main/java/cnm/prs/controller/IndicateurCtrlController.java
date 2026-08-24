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

import cnm.prs.dto.IndicateurCtrlDto;
import cnm.prs.service.IndicateurCtrlService;

/**
 * Contrôleur REST pour la ressource {@code indicateur-ctrls} (table {@code t_indicateur_ctrl}).
 *
 * <p>Périmètre <strong>nominatif</strong> (dans {@link cnm.prs.service.IndicateurCtrlService}) :
 * Président/Administrateur voient tout, un contrôleur ne voit que ses propres indicateurs, la PRMP ne
 * voit rien. L'écriture est réservée à l'{@code ADMINISTRATEUR} : un indicateur de performance que le
 * contrôleur évalué pourrait éditer ne mesurerait plus rien.</p>
 */
@RestController
@RequestMapping("/api/indicateur-ctrls")
public class IndicateurCtrlController {

    private final IndicateurCtrlService service;

    public IndicateurCtrlController(IndicateurCtrlService service) {
        this.service = service;
    }

    @GetMapping
    public List<IndicateurCtrlDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public IndicateurCtrlDto findById(@PathVariable Integer id) {
        return service.findById(id);
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PostMapping
    public ResponseEntity<IndicateurCtrlDto> create(@Valid @RequestBody IndicateurCtrlDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PutMapping("/{id}")
    public IndicateurCtrlDto update(@PathVariable Integer id, @Valid @RequestBody IndicateurCtrlDto dto) {
        return service.update(id, dto);
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
