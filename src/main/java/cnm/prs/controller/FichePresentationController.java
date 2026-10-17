package cnm.prs.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.FichePresentationDto;
import cnm.prs.service.FichePresentationService;

/**
 * ⚠️ 2026-10-17 (demande front « pieces-dmc-par-les-donnees », §B2) — la fiche de présentation d'un dossier né d'une fiche validée,
 * produite de ses données (même garde de lecture que {@code GET /api/dossiers/{id}}).
 */
@RestController
@RequestMapping("/api/dossiers/{idDossier}/fiche-presentation")
public class FichePresentationController {

    private final FichePresentationService service;

    public FichePresentationController(FichePresentationService service) {
        this.service = service;
    }

    /** Les données ; 403 hors périmètre, 404, 409 {@code FICHE_PRESENTATION_INDISPONIBLE} sans fiche validée. */
    @GetMapping
    public FichePresentationDto lire(@PathVariable Integer idDossier) {
        return service.lire(idDossier);
    }

    /** Le PDF, pour l'impression ; mêmes refus. */
    @GetMapping("/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Integer idDossier) {
        return Telechargements.fichier(service.nomPdf(idDossier), "pdf", service.pdf(idDossier));
    }
}
