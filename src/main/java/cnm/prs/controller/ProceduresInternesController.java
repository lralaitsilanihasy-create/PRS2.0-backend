package cnm.prs.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.ProcedureInterneDto;
import cnm.prs.service.ProceduresInternesService;

/**
 * ⚠️ 2026-10-06 (demande front « la liste des procédures en ligne », §B1) — {@code GET /api/fiches-marche/en-ligne} : les fiches en
 * remise électronique, toutes pour l'Administrateur, les siennes (titulaire ou intérim) pour le responsable ; {@code []} sinon.
 */
@RestController
public class ProceduresInternesController {

    private final ProceduresInternesService service;

    public ProceduresInternesController(ProceduresInternesService service) {
        this.service = service;
    }

    @GetMapping("/api/fiches-marche/en-ligne")
    public List<ProcedureInterneDto> lister() {
        return service.lister();
    }
}
