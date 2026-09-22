package cnm.prs.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.DossierDto;
import cnm.prs.dto.RattachementFicheRequest;
import cnm.prs.service.FicheMarcheDossierService;
import jakarta.validation.Valid;

/**
 * ⚠️ Fiche marché, lot 1b (demande front du 2026-09-23, §B3) — le <strong>rattachement de secours</strong> d'une fiche
 * validée à un dossier {@code DAO} créé à la main, et son retrait tant que le dossier est brouillon. PRMP et UGPM
 * propriétaires ; gardes et codes 409 dans {@link FicheMarcheDossierService}. Le chemin principal — la fiche produit
 * le dossier — est {@code POST /api/fiches-marche/{idDmc}/dossier}.
 */
@RestController
@RequestMapping("/api/dossiers/{idDossier}/fiche-marche")
@PreAuthorize("hasAnyRole('PRMP', 'UGPM')")
public class DossierFicheMarcheController {

    private final FicheMarcheDossierService service;

    public DossierFicheMarcheController(FicheMarcheDossierService service) {
        this.service = service;
    }

    /** Pose {@code ID_DMC} ; renvoie le dossier, bloc {@code ficheMarche} compris. */
    @PutMapping
    public DossierDto rattacher(@PathVariable Integer idDossier, @Valid @RequestBody RattachementFicheRequest corps) {
        return service.rattacher(idDossier, corps.idDmc());
    }

    /** Retire {@code ID_DMC} tant que le dossier est brouillon ; renvoie le dossier. */
    @DeleteMapping
    public DossierDto detacher(@PathVariable Integer idDossier) {
        return service.detacher(idDossier);
    }
}
