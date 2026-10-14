package cnm.prs.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.service.CalendrierAnnuelService;

/**
 * ⚠️ 2026-10-14 (demande front « q-piece », §B2) — le calendrier annuel de passation produit à partir du plan, lisible depuis le
 * dossier de planification et depuis un dossier produit par une fiche marché (même garde de lecture que {@code GET /api/dossiers/{id}}).
 */
@RestController
@RequestMapping("/api/dossiers/{idDossier}/calendrier-annuel")
public class CalendrierAnnuelController {

    private final CalendrierAnnuelService service;

    public CalendrierAnnuelController(CalendrierAnnuelService service) {
        this.service = service;
    }

    /** Le PDF ; 403 hors périmètre, 404, 409 {@code CALENDRIER_INDISPONIBLE} pour un dossier sans plan dans l'application. */
    @GetMapping
    public ResponseEntity<byte[]> pdf(@PathVariable Integer idDossier) {
        return Telechargements.fichier(service.nomPdf(idDossier), "pdf", service.pdf(idDossier));
    }
}
