package cnm.prs.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.AvisEnDonneesDto;
import cnm.prs.service.AvisEnDonneesService;

/**
 * ⚠️ 2026-10-18 (demande front « avis-specifique-en-donnees », §B1) — le projet d'avis spécifique (ou de lettre d'invitation) d'un
 * dossier né d'une fiche validée, en données (même garde de lecture que {@code GET /api/dossiers/{id}}).
 */
@RestController
@RequestMapping("/api/dossiers/{idDossier}/avis-specifique")
public class AvisEnDonneesController {

    private final AvisEnDonneesService service;

    public AvisEnDonneesController(AvisEnDonneesService service) {
        this.service = service;
    }

    /** Les mentions et leur valeur ; 403 hors périmètre, 404, 409 {@code AVIS_INDISPONIBLE}. */
    @GetMapping
    public AvisEnDonneesDto lire(@PathVariable Integer idDossier) {
        return service.lire(idDossier);
    }
}
