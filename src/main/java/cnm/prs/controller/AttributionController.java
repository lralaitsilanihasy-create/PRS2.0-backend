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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.AttributionDto;
import cnm.prs.entity.AttributionExplication;
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

    /** ⚠️ 2b (§B3) — 409 {@code LOT_INFRUCTUEUX}, {@code AVIS_NON_RENDU}, {@code AVIS_DEFAVORABLE}, {@code OFFRE_NON_PROPOSEE}, {@code DEJA_ATTRIBUE}. */
    @PostMapping("/lots/{lot}/attribuer")
    public AttributionDto attribuer(@PathVariable Long idDmc, @PathVariable Integer lot,
            @RequestBody(required = false) AttributionDto.AttribuerRequest r) {
        return service.attribuer(idDmc, lot, r);
    }

    /** ⚠️ 2b (§B4.1) — 400 {@code DATE_AFFICHAGE_OBLIGATOIRE}, {@code DATE_AFFICHAGE_INVALIDE} ; 409 {@code NON_ATTRIBUE}, {@code DEJA_INFORME}. */
    @PostMapping("/lots/{lot}/informer")
    public AttributionDto informer(@PathVariable Long idDmc, @PathVariable Integer lot,
            @RequestBody(required = false) AttributionDto.InformerRequest r) {
        return service.informer(idDmc, lot, r);
    }

    /** ⚠️ 2b (§B4.1) — la lettre d'un candidat du lot, en PDF ou en Word ({@code ?format=docx}). */
    @GetMapping("/lots/{lot}/lettres/{idOffre}")
    public ResponseEntity<byte[]> lettre(@PathVariable Long idDmc, @PathVariable Integer lot, @PathVariable String idOffre,
            @RequestParam(required = false) String format) {
        return Telechargements.lettre(service.lettre(idDmc, lot, idOffre), "docx".equalsIgnoreCase(format));
    }

    /** ⚠️ 2b (§B4.2) — multipart {@code texte} + {@code fichier} facultatif ; 409 {@code DEJA_REPONDU}. */
    @PostMapping(value = "/explications/{id}/reponse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttributionDto.Explication repondre(@PathVariable Long idDmc, @PathVariable Long id,
            @RequestParam(value = "texte", required = false) String texte, @RequestPart(value = "fichier", required = false) MultipartFile fichier) {
        return service.repondreExplication(idDmc, id, texte, fichier);
    }

    /** ⚠️ 2b (§B4.2) — le fichier joint à une réponse ; 404 sans fichier. */
    @GetMapping("/explications/{id}/fichier")
    public ResponseEntity<byte[]> fichier(@PathVariable Long idDmc, @PathVariable Long id) {
        AttributionExplication e = service.fichierExplication(idDmc, id);
        return Telechargements.fichier(e.getReponseNom(), e.getReponseFormat(), e.getReponseContenu());
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

    /**
     * ⚠️ 2d-1 (§B6) — {@code { motif, decision{ reference, date }, suite? }} ; PRMP ; 409 {@code EVALUATION_NON_CLOSE}, {@code DEJA_ATTRIBUE},
     * {@code DEJA_INFRUCTUEUX}, {@code INFRUCTUOSITE_NON_PROPOSEE}.
     */
    @PostMapping("/lots/{lot}/infructueux")
    public AttributionDto infructueux(@PathVariable Long idDmc, @PathVariable Integer lot,
            @RequestBody(required = false) AttributionDto.InfructuositeRequest r) {
        return service.declarerInfructueux(idDmc, lot, r);
    }

    /** ⚠️ 2d-1 (Q3) — {@code { motif }} ; PRMP ; après l'avis défavorable : l'évaluation reprend (409 {@code AVIS_NON_DEFAVORABLE}…). */
    @PostMapping("/lots/{lot}/reprendre")
    public AttributionDto reprendre(@PathVariable Long idDmc, @PathVariable Integer lot, @RequestBody(required = false) AttributionDto.RepriseRequest r) {
        return service.reprendre(idDmc, lot, r);
    }

    /** ⚠️ 2d-1 (Q3) — le rapport d'évaluation archivé par une reprise (PDF ; {@code ?format=docx}). */
    @GetMapping("/reprises/{id}/rapport")
    public ResponseEntity<byte[]> rapportArchive(@PathVariable Long idDmc, @PathVariable Long id, @RequestParam(required = false) String format) {
        boolean docx = "docx".equalsIgnoreCase(format);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, Telechargements.disposition("rapport-archive-" + idDmc + "-" + id + (docx ? ".docx" : ".pdf")))
                .contentType(docx ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                        : MediaType.APPLICATION_PDF)
                .body(service.rapportArchive(idDmc, id, docx));
    }
}
