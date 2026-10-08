package cnm.prs.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.TechniqueDto;
import cnm.prs.service.EvaluationTechniqueService;

/**
 * ⚠️ 2026-10-08 (lot 3 PI, tranche PI-c, §B4 ; V87) — l'évaluation technique des propositions de prestations intellectuelles : la
 * grille de chaque membre, les moyennes et les écarts, l'arrêt et la réouverture de l'étape par le président. Gardes dans le service.
 */
@RestController
@RequestMapping("/api/fiches-marche/{idDmc}/evaluation/technique")
public class EvaluationTechniqueController {

    private final EvaluationTechniqueService service;

    public EvaluationTechniqueController(EvaluationTechniqueService service) {
        this.service = service;
    }

    @GetMapping
    public TechniqueDto lire(@PathVariable Long idDmc) {
        return service.lire(idDmc);
    }

    /** {@code { notes[{ element, note, motif }] }} : la grille du membre appelant pour la proposition. */
    @PutMapping("/offres/{idOffre}/notes")
    public TechniqueDto noter(@PathVariable Long idDmc, @PathVariable String idOffre, @RequestBody(required = false) TechniqueDto.NotesRequest r) {
        return service.noter(idDmc, idOffre, r);
    }

    /** {@code { observation? }} ; président ; 409 {@code NOTATION_INCOMPLETE}. */
    @PostMapping("/lots/{lot}/arreter")
    public TechniqueDto arreter(@PathVariable Long idDmc, @PathVariable Integer lot, @RequestBody(required = false) TechniqueDto.ArretRequest r) {
        return service.arreter(idDmc, lot, r);
    }

    /** {@code { motif }} ; président. */
    @PostMapping("/lots/{lot}/rouvrir")
    public TechniqueDto rouvrir(@PathVariable Long idDmc, @PathVariable Integer lot, @RequestBody(required = false) TechniqueDto.ReouvertureRequest r) {
        return service.rouvrir(idDmc, lot, r);
    }
}
