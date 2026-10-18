package cnm.prs.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import cnm.prs.dto.PointsCtrlDto;
import cnm.prs.service.PointsCtrlService;

/**
 * Contrôleur REST pour la ressource {@code points-ctrls} (table {@code tr_points_ctrl}).
 */
@RestController
@RequestMapping("/api/points-ctrls")
public class PointsCtrlController {

    private final PointsCtrlService service;

    public PointsCtrlController(PointsCtrlService service) {
        this.service = service;
    }

    /**
     * Liste filtrable : {@code ?typeDossier=} (famille, écran admin) ; {@code ?sousType=} (grille
     * <strong>effective</strong> du sous-type = points communs de la famille + spécifiques — requête de
     * l'écran d'examen). Valeur inconnue ou sous-type hors famille → 400.
     */
    @GetMapping
    public List<PointsCtrlDto> findAll(@RequestParam(required = false) String typeDossier,
            @RequestParam(required = false) String sousType) {
        return service.findAll(typeDossier, sousType);
    }

    /**
     * ⚠️ 2026-10-18 (demande front « grille-contrat-cadre », §B2) — le bilan des codes de la fiche cités par les points : un code
     * inconnu, retiré, hors des catégories du point ou absent pour l'une de ses formes est en écart.
     */
    @GetMapping("/controle-codes")
    public cnm.prs.dto.CodesCitesPointsCtrlDto controleCodes() {
        return service.controleCodes();
    }

    @GetMapping("/{id}")
    public PointsCtrlDto findById(@PathVariable Integer id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<PointsCtrlDto> create(@Valid @RequestBody PointsCtrlDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/{id}")
    public PointsCtrlDto update(@PathVariable Integer id, @Valid @RequestBody PointsCtrlDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
