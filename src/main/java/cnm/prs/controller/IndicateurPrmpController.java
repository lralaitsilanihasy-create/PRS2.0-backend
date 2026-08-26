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

import cnm.prs.dto.IndicateurPrmpDto;
import cnm.prs.service.IndicateurPrmpService;

/**
 * Contrôleur REST pour la ressource {@code indicateur-prmps} (table {@code t_indicateur_prmp}).
 *
 * <p>⚠️ LOT 3a (2026-08-26) — §3.1 « Mes indicateurs [Lecture] ». <strong>Lecture</strong> ouverte à
 * tout authentifié mais <strong>scopée dans le service</strong> : la PRMP (et l'UGPM de sa tutelle) ne
 * voit que les lignes portant son {@code ID_PRMP} ; le Président et l'Administrateur voient tout ; les
 * autres profils ne voient rien. <strong>Écriture</strong> : Administrateur seul — ces lignes sont
 * dérivées de {@code v_performance_prmp}, jamais saisies par la PRMP elle-même.</p>
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

    /** ⚠️ LOT 3a (2026-08-26) — §3.1 : écriture réservée à l'Administrateur (alimentation système). */
    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public ResponseEntity<IndicateurPrmpDto> create(@Valid @RequestBody IndicateurPrmpDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    /** ⚠️ LOT 3a (2026-08-26) — Administrateur seul (voir création). */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public IndicateurPrmpDto update(@PathVariable Integer id, @Valid @RequestBody IndicateurPrmpDto dto) {
        return service.update(id, dto);
    }

    /** ⚠️ LOT 3a (2026-08-26) — Administrateur seul (voir création). */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
