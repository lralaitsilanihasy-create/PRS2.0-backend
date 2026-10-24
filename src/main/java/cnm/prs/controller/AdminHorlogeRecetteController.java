package cnm.prs.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.HorlogeRecetteDto;
import cnm.prs.service.HorlogeRecetteService;
import jakarta.validation.Valid;

/**
 * ⚠️ 2026-10-24 (demande front « horloge-recette », R1 ; ADR-0016) — l'horloge de recette, un geste de
 * l'<strong>Administrateur</strong>, en recette seulement ({@code app.recette.horloge.active=true} ; 404 ailleurs).
 */
@RestController
@RequestMapping("/api/admin/horloge-recette")
@PreAuthorize("hasRole('ADMINISTRATEUR')")
@ConditionalOnProperty(name = "app.recette.horloge.active", havingValue = "true")
public class AdminHorlogeRecetteController {

    private final HorlogeRecetteService service;

    public AdminHorlogeRecetteController(HorlogeRecetteService service) {
        this.service = service;
    }

    @GetMapping
    public HorlogeRecetteDto.Etat etat() {
        return service.etat();
    }

    /** 400 {@code MAINTENANT_OU_AVANCER}, {@code DUREE_INVALIDE}, motif absent. */
    @PutMapping
    public HorlogeRecetteDto.Etat regler(@Valid @RequestBody HorlogeRecetteDto.Reglage reglage) {
        return service.regler(reglage);
    }

    /** Retour à l'heure réelle. */
    @DeleteMapping
    public HorlogeRecetteDto.Etat reinitialiser(@RequestParam(required = false) String motif) {
        return service.reinitialiser(motif);
    }
}
