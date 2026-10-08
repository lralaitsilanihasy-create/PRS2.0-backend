package cnm.prs.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.SansSuiteDto;
import cnm.prs.service.SansSuiteService;

/**
 * ⚠️ 2026-10-08 (lot 2, tranche 2d-3, §B6, Q10 ; V93) — la déclaration sans suite (art. 55) : la demande de la PRMP (dossier DSS au
 * circuit de la Commission), l'avis suivi, la déclaration après l'avis favorable. Gardes dans le service.
 */
@RestController
@RequestMapping("/api/fiches-marche/{idDmc}/sans-suite")
public class SansSuiteController {

    private final SansSuiteService service;

    public SansSuiteController(SansSuiteService service) {
        this.service = service;
    }

    @GetMapping
    public SansSuiteDto lire(@PathVariable Long idDmc) {
        return service.lire(idDmc);
    }

    /** {@code { motifs }} : PRMP ; crée le dossier DSS (brouillon) et sa pièce des motifs. */
    @PostMapping
    public SansSuiteDto demander(@PathVariable Long idDmc, @RequestBody(required = false) SansSuiteDto.DemandeRequest r) {
        return service.demander(idDmc, r);
    }

    /** {@code { decision{ reference, date } }} : PRMP, après l'avis favorable. */
    @PostMapping("/declarer")
    public SansSuiteDto declarer(@PathVariable Long idDmc, @RequestBody(required = false) SansSuiteDto.DeclarationRequest r) {
        return service.declarer(idDmc, r);
    }

    /** Les motifs d'une demande (PDF ; {@code ?format=docx}). */
    @GetMapping("/{id}/motifs")
    public ResponseEntity<byte[]> motifs(@PathVariable Long idDmc, @PathVariable Long id, @RequestParam(required = false) String format) {
        boolean docx = "docx".equalsIgnoreCase(format);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, Telechargements.disposition("motifs-sans-suite-" + idDmc + (docx ? ".docx" : ".pdf")))
                .contentType(docx ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                        : MediaType.APPLICATION_PDF)
                .body(service.motifs(idDmc, id, docx));
    }
}
