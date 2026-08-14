package cnm.prs.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import jakarta.validation.Valid;

import cnm.prs.dto.DemandeRetraitDecisionRequest;
import cnm.prs.dto.DemandeRetraitDto;
import cnm.prs.service.DemandeRetraitService;

/**
 * Contrôleur REST pour la ressource {@code demande-retraits} (table {@code t_demande_retrait}).
 */
@RestController
@RequestMapping("/api/demande-retraits")
public class DemandeRetraitController {

    private final DemandeRetraitService service;

    public DemandeRetraitController(DemandeRetraitService service) {
        this.service = service;
    }

    @GetMapping
    public List<DemandeRetraitDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public DemandeRetraitDto findById(@PathVariable Integer id) {
        return service.findById(id);
    }

    /**
     * Écran « Mes demandes de retrait » de la PRMP. ⚠️ À l'ouverture, marque l'écran consulté
     * (met à jour {@code dateDerniereVue}), ce qui remet à zéro le compteur de nouveautés.
     */
    @PreAuthorize("hasRole('PRMP')")
    @GetMapping("/mes-demandes")
    public List<DemandeRetraitDto> mesDemandes() {
        return service.mesDemandes();
    }

    /** File « à valider » du CC (sa localité) / Président (toutes localités) : demandes EN_ATTENTE. */
    @PreAuthorize("@perm.peutExercer('CHEF_COMMISSION')")
    @GetMapping("/a-valider")
    public List<DemandeRetraitDto> aValider() {
        return service.aValider();
    }

    /** Historique des demandes décidées (ACCEPTEE / REFUSEE), même scope. */
    @PreAuthorize("@perm.peutExercer('CHEF_COMMISSION')")
    @GetMapping("/historique")
    public List<DemandeRetraitDto> historique() {
        return service.historique();
    }

    // Demande de retrait : action de la PRMP (§3.1, Module 11).
    @PreAuthorize("hasRole('PRMP')")
    @PostMapping
    public ResponseEntity<DemandeRetraitDto> create(@Valid @RequestBody DemandeRetraitDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    // Décision : acceptation. Le service vérifie rôle↔localité (CC de la localité du dossier ou Président).
    @PreAuthorize("@perm.peutExercer('CHEF_COMMISSION')")
    @PostMapping("/{id}/accepter")
    public DemandeRetraitDto accepter(@PathVariable Integer id) {
        return service.accepter(id);
    }

    // Décision : refus (motif optionnel). Le service vérifie rôle↔localité.
    @PreAuthorize("@perm.peutExercer('CHEF_COMMISSION')")
    @PostMapping("/{id}/refuser")
    public DemandeRetraitDto refuser(@PathVariable Integer id,
            @RequestBody(required = false) @Valid DemandeRetraitDecisionRequest req) {
        return service.refuser(id, req != null ? req.motif() : null);
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
