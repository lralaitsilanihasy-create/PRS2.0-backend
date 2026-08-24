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

import cnm.prs.dto.AnomalieDto;
import cnm.prs.service.AnomalieService;

/**
 * Contrôleur REST pour la ressource {@code anomalies} (table {@code t_anomalie}).
 *
 * <p>Une anomalie signale un défaut sur une <strong>ligne de marché</strong> : la lecture est scopée au
 * périmètre du marché parent (dans {@link cnm.prs.service.AnomalieService}). L'écriture est réservée à
 * l'{@code ADMINISTRATEUR} — les anomalies sont <strong>constatées par le serveur</strong> (règles
 * {@code tr_regle_anomalie}, revue de transcription à l'import du PPM) et aucun écran ne les crée.
 * Laisser un client en forger, ou réécrire {@code IM_TRAITEMENT} / {@code STATUT}, reviendrait à laisser
 * clore une anomalie au nom d'un tiers.</p>
 */
@RestController
@RequestMapping("/api/anomalies")
public class AnomalieController {

    private final AnomalieService service;

    public AnomalieController(AnomalieService service) {
        this.service = service;
    }

    @GetMapping
    public List<AnomalieDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public AnomalieDto findById(@PathVariable Integer id) {
        return service.findById(id);
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PostMapping
    public ResponseEntity<AnomalieDto> create(@Valid @RequestBody AnomalieDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PutMapping("/{id}")
    public AnomalieDto update(@PathVariable Integer id, @Valid @RequestBody AnomalieDto dto) {
        return service.update(id, dto);
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
