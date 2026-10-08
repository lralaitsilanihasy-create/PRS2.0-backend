package cnm.prs.controller;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.EvaluationDto;
import cnm.prs.dto.FinanciereDto;
import cnm.prs.dto.NegociationDto;
import cnm.prs.dto.TechniqueDto;
import cnm.prs.entity.Negociation;
import cnm.prs.service.EvaluationFinanciereService;
import cnm.prs.service.NegociationService;

/**
 * ⚠️ 2026-10-08 (lot 3 PI, tranche PI-d2a, §B5, §B6 ; V89) — l'évaluation financière des propositions de prestations intellectuelles,
 * leur classement selon la méthode, et la négociation. Sous l'accès de l'évaluation ({@code /evaluation/**}) ; gardes dans les services.
 */
@RestController
@RequestMapping("/api/fiches-marche/{idDmc}/evaluation")
public class EvaluationFinanciereController {

    private final EvaluationFinanciereService service;
    private final NegociationService negociation;

    public EvaluationFinanciereController(EvaluationFinanciereService service, NegociationService negociation) {
        this.service = service;
        this.negociation = negociation;
    }

    // ------------------------------------------------------------------ §B5

    @GetMapping("/financiere")
    public FinanciereDto lire(@PathVariable Long idDmc) {
        return service.lire(idDmc);
    }

    @GetMapping("/financiere/offres/{idFinanciere}/corrections-proposees")
    public List<EvaluationDto.CorrectionProposee> correctionsProposees(@PathVariable Long idDmc, @PathVariable String idFinanciere) {
        return service.correctionsProposees(idDmc, idFinanciere);
    }

    /** {@code { prixLu?, corrections[], remboursables, motifRemboursables?, refusCandidat? }} : membre déclaré sans conflit. */
    @PutMapping("/financiere/offres/{idFinanciere}")
    public FinanciereDto saisir(@PathVariable Long idDmc, @PathVariable String idFinanciere,
            @RequestBody(required = false) FinanciereDto.SaisieRequest r) {
        return service.saisir(idDmc, idFinanciere, r);
    }

    /** {@code { ordre[idOffre…], motif }} : membre déclaré sans conflit. */
    @PostMapping("/financiere/lots/{lot}/departager")
    public FinanciereDto departager(@PathVariable Long idDmc, @PathVariable Integer lot, @RequestBody(required = false) FinanciereDto.DepartageRequest r) {
        return service.departager(idDmc, lot, r);
    }

    @PostMapping("/financiere/lots/{lot}/arreter")
    public FinanciereDto arreter(@PathVariable Long idDmc, @PathVariable Integer lot, @RequestBody(required = false) TechniqueDto.ArretRequest r) {
        return service.arreter(idDmc, lot, r);
    }

    @PostMapping("/financiere/lots/{lot}/rouvrir")
    public FinanciereDto rouvrir(@PathVariable Long idDmc, @PathVariable Integer lot, @RequestBody(required = false) TechniqueDto.ReouvertureRequest r) {
        return service.rouvrir(idDmc, lot, r);
    }

    // ------------------------------------------------------------------ §B6

    @GetMapping("/negociation")
    public NegociationDto negociations(@PathVariable Long idDmc) {
        return negociation.lire(idDmc);
    }

    /** {@code { prevueLe?, lieu? }} : PRMP ou UGPM ; le candidat dont c'est le tour. */
    @PostMapping("/negociation/lots/{lot}/ouvrir")
    public NegociationDto ouvrir(@PathVariable Long idDmc, @PathVariable Integer lot, @RequestBody(required = false) NegociationDto.OuvertureRequest r) {
        return negociation.ouvrir(idDmc, lot, r);
    }

    @PutMapping(value = "/negociation/{id}/piece", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public NegociationDto joindre(@PathVariable Long idDmc, @PathVariable Long id, @RequestPart("fichier") MultipartFile fichier) {
        return negociation.joindre(idDmc, id, fichier);
    }

    @GetMapping("/negociation/{id}/piece")
    public ResponseEntity<byte[]> piece(@PathVariable Long idDmc, @PathVariable Long id) {
        Negociation n = negociation.piece(idDmc, id);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, Telechargements.disposition(n.getPieceNom() == null ? "piece" : n.getPieceNom()))
                .contentType(n.getPieceType() == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(n.getPieceType()))
                .body(n.getPiece());
    }

    /** {@code { resultat: REUSSIE|ECHOUEE, dateNegociation, lieu?, texte, motif? }} : PRMP ou UGPM ; produit le PV. */
    @PostMapping("/negociation/{id}/conclure")
    public NegociationDto conclure(@PathVariable Long idDmc, @PathVariable Long id, @RequestBody(required = false) NegociationDto.ConclusionRequest r) {
        return negociation.conclure(idDmc, id, r);
    }

    @GetMapping("/negociation/{id}/pv")
    public ResponseEntity<byte[]> pv(@PathVariable Long idDmc, @PathVariable Long id, @RequestParam(required = false) String format) {
        boolean docx = "docx".equalsIgnoreCase(format);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, Telechargements.disposition("pv-negociation-" + idDmc + "-" + id + (docx ? ".docx" : ".pdf")))
                .contentType(docx ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                        : MediaType.APPLICATION_PDF)
                .body(negociation.pv(idDmc, id, docx));
    }
}
