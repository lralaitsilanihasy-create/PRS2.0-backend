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

import cnm.prs.dto.PvNavetteDto;
import cnm.prs.service.PvNavetteService;

/**
 * Contrôleur REST pour la ressource {@code pv-navettes} (table {@code t_pv_navette}).
 *
 * <p>⚠️ LOT 3a (2026-08-26) — §3.5 « aucune navette ne peut être supprimée » et §1.</p>
 * <ul>
 *   <li><strong>PUT</strong> : refusé pour <em>tous</em>, Administrateur compris — <strong>409</strong>.
 *       Le {@code DELETE} était déjà bloqué mais le {@code PUT} contournait l'immuabilité en
 *       réécrivant sens, acteur, date et commentaire d'une navette déjà tracée. Aucune règle
 *       {@code @PreAuthorize} ici : le refus doit être un 409 « la navette est immuable » pour tout le
 *       monde, jamais un 403 qui laisserait croire qu'un autre profil y arriverait.</li>
 *   <li><strong>POST</strong> : Administrateur seul. Les vraies navettes naissent du flux PV
 *       (soumission / retour rectification / acceptation), qui les insère lui-même.</li>
 *   <li><strong>Lecture</strong> : scopée dans le service à la localité du contrôleur
 *       (Président/Administrateur : tout). La PRMP n'y a pas accès — elle reçoit la synthèse par le
 *       PV, pas le détail de la navette interne (§3.1).</li>
 * </ul>
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

    /** ⚠️ LOT 3a (2026-08-26) — §3.5 : création générique réservée à l'Administrateur (voir en-tête). */
    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public ResponseEntity<PvNavetteDto> create(@Valid @RequestBody PvNavetteDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    /**
     * ⚠️ LOT 3a (2026-08-26) — §3.5 : <strong>toujours 409</strong>, la navette est immuable.
     * Volontairement sans {@code @PreAuthorize} : le refus ne dépend d'aucun profil.
     */
    @PutMapping("/{id}")
    public PvNavetteDto update(@PathVariable Integer id, @Valid @RequestBody PvNavetteDto dto) {
        return service.update(id, dto);
    }

    /** §3.5 : suppression impossible — toujours 409 (comportement inchangé). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
