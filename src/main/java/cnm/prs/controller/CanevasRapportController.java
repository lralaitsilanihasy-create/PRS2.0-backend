package cnm.prs.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.CanevasRapportDto;
import cnm.prs.service.CanevasRapportService;

/**
 * ⚠️ 2026-10-17 (demande front « canevas-rapport-evaluation », §B1) — le canevas de rapport d'évaluation d'un dossier né d'une fiche validée, hors contrat-cadre,
 * produite de ses données (même garde de lecture que {@code GET /api/dossiers/{id}}).
 */
@RestController
@RequestMapping("/api/dossiers/{idDossier}/canevas-rapport")
public class CanevasRapportController {

    private final CanevasRapportService service;

    public CanevasRapportController(CanevasRapportService service) {
        this.service = service;
    }

    /** Les données ; 403 hors périmètre, 404, 409 {@code CANEVAS_INDISPONIBLE} sans fiche validée ou au contrat-cadre. */
    @GetMapping
    public CanevasRapportDto lire(@PathVariable Integer idDossier) {
        return service.lire(idDossier);
    }

    /** Le PDF, pour l'impression ; mêmes refus. */
    @GetMapping("/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Integer idDossier) {
        return Telechargements.fichier(service.nomPdf(idDossier), "pdf", service.pdf(idDossier));
    }
}
