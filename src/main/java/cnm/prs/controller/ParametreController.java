package cnm.prs.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import cnm.prs.dto.InterrupteurDto;
import cnm.prs.service.ParametreService;

/**
 * Paramètres système exposés à l'API ({@code t_parametre}) — pour l'instant, l'interrupteur
 * global des actualités (spec du 2026-08-18).
 */
@RestController
@RequestMapping("/api/parametres")
public class ParametreController {

    private final ParametreService service;

    public ParametreController(ParametreService service) {
        this.service = service;
    }

    /** État de l'interrupteur (tout authentifié — le front s'en sert pour l'écran Admin). */
    @GetMapping("/actualites-actives")
    public InterrupteurDto actualitesActives() {
        return new InterrupteurDto(service.actualitesActives());
    }

    /** Bascule de l'interrupteur global : coupe/rétablit le modal pour tous, d'un coup. */
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PutMapping("/actualites-actives")
    public InterrupteurDto basculer(@Valid @RequestBody InterrupteurDto corps) {
        return new InterrupteurDto(service.basculerActualites(corps.actif()));
    }
}
