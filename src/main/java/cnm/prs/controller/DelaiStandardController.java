package cnm.prs.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.DelaiStandardDto;
import cnm.prs.service.DelaiStandardService;
import jakarta.validation.Valid;

/**
 * ⚠️ Référentiel administrable des délais standards par étape (arbitrage ②, 2026-09-01).
 *
 * <p><strong>Lecture ouverte à tout utilisateur authentifié</strong> : ces délais expliquent la date
 * annoncée à la PRMP, et une date qu'on ne peut pas expliquer se conteste mal. <strong>Écriture
 * réservée à l'Administrateur</strong> — c'est un paramétrage de pilotage, pas une donnée de dossier.</p>
 */
@RestController
@RequestMapping("/api/delais-standards")
public class DelaiStandardController {

    private final DelaiStandardService service;

    public DelaiStandardController(DelaiStandardService service) {
        this.service = service;
    }

    /** Les huit étapes du circuit avec leur délai standard, dans l'ordre de parcours. */
    @GetMapping
    public List<DelaiStandardDto> tableau() {
        return service.tableau();
    }

    /** Règle le délai d'une étape. 404 si l'étape n'existe pas, 400 si le délai est inférieur à 1. */
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PutMapping("/{etape}")
    public DelaiStandardDto definir(@PathVariable String etape, @Valid @RequestBody DelaiStandardDto dto) {
        return service.definir(etape, dto);
    }
}
