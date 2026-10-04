package cnm.prs.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.ConservationOffresDto;
import cnm.prs.service.ConservationOffresService;

/**
 * ⚠️ 2026-10-04 (arbitrages du pilote après le lot 4, §B4.2, Q3) — la purge des offres au terme de leur conservation, un geste de
 * l'<strong>Administrateur</strong>.
 */
@RestController
@RequestMapping("/api/admin/offres/conservation")
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class AdminConservationOffresController {

    private final ConservationOffresService service;

    public AdminConservationOffresController(ConservationOffresService service) {
        this.service = service;
    }

    @GetMapping
    public ConservationOffresDto echues() {
        return service.echues();
    }

    /** 404 sans séance ; 409 {@code CONSERVATION_NON_FIXEE}, {@code CONSERVATION_EN_COURS} ({@code details.echeance}). */
    @PostMapping("/{idDmc}/purger")
    public ConservationOffresDto.Purge purger(@PathVariable Long idDmc) {
        return service.purger(idDmc);
    }
}
