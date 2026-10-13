package cnm.prs.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.InstantaneDto;
import cnm.prs.service.InstantaneService;
import tools.jackson.databind.JsonNode;

/**
 * ⚠️ R4 (manuel révisé pour l'application, lot 1, §B5 ; V104, 2026-10-13) — les instantanés figés à la soumission d'un dossier,
 * pour les profils CNM et les lecteurs du dossier (même garde que {@code GET /api/dossiers/{id}}).
 */
@RestController
@RequestMapping("/api/dossiers/{idDossier}/instantanes")
public class InstantaneController {

    private final InstantaneService service;

    public InstantaneController(InstantaneService service) {
        this.service = service;
    }

    /** La liste : un par (soumission, écran), avec l'empreinte et la date. */
    @GetMapping
    public List<InstantaneDto> lister(@PathVariable Integer idDossier) {
        return service.lister(idDossier);
    }

    /** Le JSON figé d'un écran : de la soumission {@code ?soumission=n}, ou de la dernière. 400 {@code ECRAN_INCONNU} ; 404. */
    @GetMapping("/{ecran}")
    public JsonNode contenu(@PathVariable Integer idDossier, @PathVariable String ecran, @RequestParam(required = false) Integer soumission) {
        return service.contenu(idDossier, ecran, soumission);
    }

    /** L'export PDF de l'écran figé (archivage avec le PV). */
    @GetMapping("/{ecran}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Integer idDossier, @PathVariable String ecran,
            @RequestParam(required = false) Integer soumission) {
        return Telechargements.fichier(service.nomPdf(idDossier, ecran, soumission), "pdf", service.pdf(idDossier, ecran, soumission));
    }
}
