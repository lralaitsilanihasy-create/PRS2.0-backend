package cnm.prs.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.ObservationPvDto;
import cnm.prs.dto.PassageObservationsRequest;
import cnm.prs.service.ObservationPvService;

import jakarta.validation.Valid;

/**
 * ⚠️ Spec « circuit des observations FAVR » (2026-08-02) — suivi des observations du PV (périmètre
 * FIGÉ) pendant le cycle rectification / vérification. Lecture : vérificateur (localité) et PRMP
 * propriétaire. Passage : Contrôleur vérificateur uniquement — décisions individuelles LEVÉE /
 * MAINTENUE, aucune création possible (rejet backend hors périmètre).
 */
@RestController
@RequestMapping("/api/observations-pv")
public class ObservationPvController {

    private final ObservationPvService service;

    public ObservationPvController(ObservationPvService service) {
        this.service = service;
    }

    /** Observations du dossier (statut courant + historique par itération). */
    @GetMapping
    public List<ObservationPvDto> parDossier(@RequestParam(name = "dossier") Integer dossier) {
        return service.parDossier(dossier);
    }

    /** Passage de vérification : une décision par observation restante (LEVEE | MAINTENUE + précision). */
    @PreAuthorize("@perm.peutExercer('VERIFICATEUR')")
    @PostMapping("/passage")
    public List<ObservationPvDto> passage(@Valid @RequestBody PassageObservationsRequest req) {
        return service.enregistrerPassage(req);
    }
}
