package cnm.prs.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.AttributionDto;
import cnm.prs.service.AttributionService;

/**
 * ⚠️ 2026-10-07 (évaluation des offres, lot 2, tranche 2a ; V79) — l'attribution d'une procédure, lot par lot, après le rapport
 * d'évaluation : l'état, le dossier de marché au contrôle de la Commission et le projet de marché. Gardes dans le service.
 */
@RestController
@RequestMapping("/api/fiches-marche/{idDmc}/attribution")
public class AttributionController {

    private final AttributionService service;

    public AttributionController(AttributionService service) {
        this.service = service;
    }

    @GetMapping
    public AttributionDto lire(@PathVariable Long idDmc) {
        return service.lire(idDmc);
    }

    /** 201 ; 409 {@code EVALUATION_NON_CLOSE}, {@code LOT_INFRUCTUEUX}, {@code DOSSIER_EXISTANT}. */
    @PostMapping("/lots/{lot}/dossier")
    public ResponseEntity<AttributionDto> creerDossier(@PathVariable Long idDmc, @PathVariable Integer lot) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creerDossier(idDmc, lot));
    }

    /** Le projet de marché du lot, en PDF ou en Word ({@code ?format=docx}). */
    @GetMapping("/lots/{lot}/projet")
    public ResponseEntity<byte[]> projet(@PathVariable Long idDmc, @PathVariable Integer lot, @RequestParam(required = false) String format) {
        boolean docx = "docx".equalsIgnoreCase(format);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, Telechargements.disposition("projet-de-marche-" + idDmc + "-lot" + lot + (docx ? ".docx" : ".pdf")))
                .contentType(docx ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                        : MediaType.APPLICATION_PDF)
                .body(service.projet(idDmc, lot, docx));
    }
}
