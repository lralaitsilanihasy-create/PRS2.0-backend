package cnm.prs.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.ContratVigueurDto;
import cnm.prs.service.ContratVigueurService;

/**
 * ⚠️ 2026-10-16 (demande front « marchés subséquents », lot MS-1) — les contrats-cadres et marchés à commandes en vigueur : la liste et
 * le détail (PRMP et UGPM : leurs procédures ; Commission et Administrateur : tous), la décision de reconduction (PRMP de la fiche).
 */
@RestController
@RequestMapping("/api/contrats-en-vigueur")
public class ContratVigueurController {

    private final ContratVigueurService service;

    public ContratVigueurController(ContratVigueurService service) {
        this.service = service;
    }

    @GetMapping
    public List<ContratVigueurDto> lister() {
        return service.lister();
    }

    @GetMapping("/{id}")
    public ContratVigueurDto lire(@PathVariable Long id) {
        return service.lire(id);
    }

    /** 400 {@code DECISION_INVALIDE}, {@code DECISION_OBLIGATOIRE}, {@code DATE_INVALIDE} ; 409 {@code CONTRAT_ECHU}, {@code NON_RECONDUCTIBLE}, {@code RECONDUCTIONS_EPUISEES}, {@code DUREE_MAX_ATTEINTE}, {@code DEJA_DECIDE}. */
    @PostMapping("/{id}/reconduction")
    public ContratVigueurDto reconduire(@PathVariable Long id, @RequestBody(required = false) ContratVigueurDto.ReconductionRequest r) {
        return service.reconduire(id, r);
    }
}
