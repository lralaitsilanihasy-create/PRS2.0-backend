package cnm.prs.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.AttributionDto;
import cnm.prs.service.AttributionService;

/**
 * ⚠️ 2026-10-07 (évaluation des offres, lot 2, tranche 2b, §B4.1) — le résultat d'une procédure en ligne publié sur sa page publique, lot
 * par lot, une fois les candidats informés : l'attributaire, le montant hors taxes, les dates d'information et d'affichage. Sans
 * session ; liste vide avant l'information.
 */
@RestController
public class ResultatsPublicsController {

    private final AttributionService service;

    public ResultatsPublicsController(AttributionService service) {
        this.service = service;
    }

    @GetMapping("/api/procedures-en-ligne/{idDmc}/resultats")
    public List<AttributionDto.ResultatPublic> resultats(@PathVariable Long idDmc) {
        return service.resultatsPublics(idDmc);
    }
}
