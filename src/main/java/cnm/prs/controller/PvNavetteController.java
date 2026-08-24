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

import jakarta.validation.Valid;

import cnm.prs.dto.PvNavetteDto;
import cnm.prs.service.PvNavetteService;

/**
 * Contrôleur REST pour la ressource {@code pv-navettes} (table {@code t_pv_navette}).
 *
 * <p><strong>Ressource en lecture seule, scopée à la localité du PV.</strong> L'historique des navettes
 * est alimenté par le serveur lui-même ({@code PvExamenService#ajouterNavette}, à la soumission, au
 * retour en rectification et à l'acceptation). Les trois verbes d'écriture restent <strong>routés</strong>
 * pour porter un refus explicite — 409 « historique immuable », comme le {@code DELETE} le faisait déjà
 * (§3.5) : un appelant apprend ainsi <em>pourquoi</em> l'écriture est refusée, là où un 405 ne dirait que
 * « mauvais verbe ».</p>
 */
@RestController
@RequestMapping("/api/pv-navettes")
public class PvNavetteController {

    private final PvNavetteService service;

    public PvNavetteController(PvNavetteService service) {
        this.service = service;
    }

    @GetMapping
    public List<PvNavetteDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public PvNavetteDto findById(@PathVariable Integer id) {
        return service.findById(id);
    }

    /** Routé pour refuser explicitement : une navette ne se forge pas (409, §3.5). */
    @PostMapping
    public ResponseEntity<PvNavetteDto> create(@Valid @RequestBody PvNavetteDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    /** Routé pour refuser explicitement : une navette ne se réécrit pas (409, §3.5). */
    @PutMapping("/{id}")
    public PvNavetteDto update(@PathVariable Integer id, @Valid @RequestBody PvNavetteDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
