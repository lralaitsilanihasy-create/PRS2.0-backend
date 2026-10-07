package cnm.prs.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.AttributionDto;
import cnm.prs.entity.AttributionPiece;
import cnm.prs.service.AttributionExecutionService;
import cnm.prs.service.AttributionService;

/**
 * ⚠️ 2026-10-07 (évaluation des offres, lot 2, tranche 2c ; V81) — de la mise au point à l'avis d'attribution : recours, pièces de
 * l'attributaire et retrait, signature, enregistrement, notification, avis. Chaque geste de la PRMP répond l'{@code AttributionDto} à
 * jour. Gardes dans le service.
 */
@RestController
@RequestMapping("/api/fiches-marche/{idDmc}/attribution")
public class AttributionExecutionController {

    private final AttributionExecutionService execution;
    private final AttributionService attribution;

    public AttributionExecutionController(AttributionExecutionService execution, AttributionService attribution) {
        this.execution = execution;
        this.attribution = attribution;
    }

    /** Multipart {@code rapport} + {@code fichier} facultatif ; 400 {@code RAPPORT_OBLIGATOIRE} ; 409 {@code NON_ATTRIBUE}, {@code DEJA_SIGNE}. */
    @PostMapping(value = "/lots/{lot}/mise-au-point", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttributionDto miseAuPoint(@PathVariable Long idDmc, @PathVariable Integer lot,
            @RequestParam(value = "rapport", required = false) String rapport, @RequestPart(value = "fichier", required = false) MultipartFile fichier) {
        execution.miseAuPoint(idDmc, lot, rapport, fichier);
        return attribution.lire(idDmc);
    }

    /** Multipart {@code type}, {@code dateReception}, {@code requerant}, {@code objet} + {@code fichier} facultatif. */
    @PostMapping(value = "/lots/{lot}/recours", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttributionDto recours(@PathVariable Long idDmc, @PathVariable Integer lot, @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "dateReception", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateReception,
            @RequestParam(value = "requerant", required = false) String requerant, @RequestParam(value = "objet", required = false) String objet,
            @RequestPart(value = "fichier", required = false) MultipartFile fichier) {
        execution.declarerRecours(idDmc, lot, type, dateReception, requerant, objet, fichier);
        return attribution.lire(idDmc);
    }

    /** Multipart {@code date}, {@code issue}, {@code motif} + {@code fichier} facultatif ; 409 {@code DEJA_DECIDE}. */
    @PostMapping(value = "/recours/{id}/decision", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttributionDto decision(@PathVariable Long idDmc, @PathVariable Long id,
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(value = "issue", required = false) String issue, @RequestParam(value = "motif", required = false) String motif,
            @RequestPart(value = "fichier", required = false) MultipartFile fichier) {
        execution.deciderRecours(idDmc, id, date, issue, motif, fichier);
        return attribution.lire(idDmc);
    }

    /** {@code { conforme, motif? }} ; 409 {@code DEJA_VERIFIEE}. */
    @PostMapping("/lots/{lot}/pieces/{id}/verifier")
    public AttributionDto verifier(@PathVariable Long idDmc, @PathVariable Integer lot, @PathVariable Long id,
            @RequestBody(required = false) AttributionDto.VerificationRequest v) {
        execution.verifierPiece(idDmc, lot, id, v);
        return attribution.lire(idDmc);
    }

    /** {@code { motif }} ; 409 {@code DELAI_EN_COURS}, {@code PIECES_CONFORMES}, {@code DEJA_SIGNE}, {@code LOT_RETIRE}. */
    @PostMapping("/lots/{lot}/retirer")
    public AttributionDto retirer(@PathVariable Long idDmc, @PathVariable Integer lot, @RequestBody(required = false) AttributionDto.RetraitRequest r) {
        execution.retirer(idDmc, lot, r);
        return attribution.lire(idDmc);
    }

    /** Multipart {@code dateSignature} + {@code fichier} (le marché signé) ; 409 {@code DELAI_ATTENTE}, {@code RECOURS_EN_COURS}, {@code PIECES_NON_CONFORMES}. */
    @PostMapping(value = "/lots/{lot}/signature", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttributionDto signer(@PathVariable Long idDmc, @PathVariable Integer lot,
            @RequestParam(value = "dateSignature", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateSignature,
            @RequestPart(value = "fichier", required = false) MultipartFile fichier) {
        execution.signer(idDmc, lot, dateSignature, fichier);
        return attribution.lire(idDmc);
    }

    /** Multipart {@code dateEnregistrement}, {@code reference} facultative + {@code fichier} (le justificatif) ; 409 {@code NON_SIGNE}. */
    @PostMapping(value = "/lots/{lot}/enregistrement", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttributionDto enregistrer(@PathVariable Long idDmc, @PathVariable Integer lot,
            @RequestParam(value = "dateEnregistrement", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(value = "reference", required = false) String reference, @RequestPart(value = "fichier", required = false) MultipartFile fichier) {
        execution.enregistrer(idDmc, lot, date, reference, fichier);
        return attribution.lire(idDmc);
    }

    /** {@code { dateNotification, dateReception? }} ; 409 {@code NON_SIGNE}, {@code NON_ENREGISTRE}, {@code DEJA_NOTIFIE}. */
    @PostMapping("/lots/{lot}/notification")
    public AttributionDto notifier(@PathVariable Long idDmc, @PathVariable Integer lot,
            @RequestBody(required = false) AttributionDto.NotificationRequest r) {
        execution.notifier(idDmc, lot, r);
        return attribution.lire(idDmc);
    }

    /** {@code { datePublication }} ; 409 {@code NON_NOTIFIE}, {@code DEJA_PUBLIE}. */
    @PostMapping("/lots/{lot}/avis")
    public AttributionDto publierAvis(@PathVariable Long idDmc, @PathVariable Integer lot, @RequestBody(required = false) AttributionDto.AvisRequest r) {
        execution.publierAvis(idDmc, lot, r);
        return attribution.lire(idDmc);
    }

    /** L'avis d'attribution publié, en PDF ou en Word ({@code ?format=docx}). */
    @GetMapping("/lots/{lot}/avis")
    public ResponseEntity<byte[]> avis(@PathVariable Long idDmc, @PathVariable Integer lot, @RequestParam(required = false) String format) {
        boolean docx = "docx".equalsIgnoreCase(format);
        return avisReponse(idDmc, lot, docx, execution.avis(idDmc, lot, docx));
    }

    /** Un fichier de l'attribution (mise au point, marché signé, enregistrement, recours, pièces de l'attributaire). */
    @GetMapping("/pieces/{id}/fichier")
    public ResponseEntity<byte[]> fichier(@PathVariable Long idDmc, @PathVariable Long id) {
        AttributionPiece p = execution.fichier(idDmc, id);
        return Telechargements.fichier(p.getNom(), p.getFormat(), p.getContenu());
    }

    static ResponseEntity<byte[]> avisReponse(Long idDmc, Integer lot, boolean docx, byte[] contenu) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, Telechargements.disposition("avis-attribution-" + idDmc + "-lot" + lot + (docx ? ".docx" : ".pdf")))
                .contentType(docx ? Telechargements.DOCX : MediaType.APPLICATION_PDF).body(contenu);
    }
}
