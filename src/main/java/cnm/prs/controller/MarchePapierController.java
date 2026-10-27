package cnm.prs.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.DossierDto;
import cnm.prs.service.MarchePapierService;

/**
 * ⚠️ 2026-10-27 (demande front « dossier-marche-remise-papier », P2) — le dossier de marché d'un lot d'une procédure à remise papier,
 * rattaché à sa procédure. Gardes dans le service.
 */
@RestController
@RequestMapping("/api/fiches-marche/{idDmc}/marche-papier")
public class MarchePapierController {

    private final MarchePapierService service;

    public MarchePapierController(MarchePapierService service) {
        this.service = service;
    }

    /**
     * 201 et le {@code DossierDto} créé, en brouillon ; 403 ; 404 (procédure, lot) ; 409 {@code DOSSIER_EXISTANT} (avec
     * {@code idDossier}), {@code PROCEDURE_ELECTRONIQUE}, {@code FICHE_NON_VALIDEE}, {@code DOSSIER_NON_CLOTURE},
     * {@code AVIS_NON_FAVORABLE}, {@code AVIS_NON_PUBLIE}, {@code SANS_SUITE_DECLAREE}.
     */
    @PostMapping("/lots/{lot}/dossier")
    public ResponseEntity<DossierDto> creerDossier(@PathVariable Long idDmc, @PathVariable Integer lot) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creerDossier(idDmc, lot));
    }
}
