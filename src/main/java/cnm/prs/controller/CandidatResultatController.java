package cnm.prs.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.AttributionDto;
import cnm.prs.entity.AttributionExplication;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.security.CurrentUser;
import cnm.prs.service.AttributionService;

/**
 * ⚠️ 2026-10-07 (évaluation des offres, lot 2, tranche 2b, §B4.1, §B4.2 ; V80) — le résultat de son offre pour le candidat, sa lettre
 * (la consulter vaut accusé de lecture de la plateforme), et ses demandes d'explication s'il n'est pas retenu. Le candidat ne voit que
 * son offre.
 */
@RestController
@RequestMapping("/api/candidat/offres/{idOffre}")
@PreAuthorize("hasRole('CANDIDAT')")
public class CandidatResultatController {

    private final AttributionService service;
    private final cnm.prs.service.AttributionExecutionService execution;

    public CandidatResultatController(AttributionService service, cnm.prs.service.AttributionExecutionService execution) {
        this.service = service;
        this.execution = execution;
    }

    /** 404 avant l'information des candidats. */
    @GetMapping("/resultat")
    public AttributionDto.Resultat resultat(@PathVariable String idOffre) {
        return service.resultat(moi(), idOffre);
    }

    /** La lettre, en PDF ou en Word ({@code ?format=docx}) ; 404 avant l'information. */
    @GetMapping("/resultat/lettre")
    public ResponseEntity<byte[]> lettre(@PathVariable String idOffre, @RequestParam(required = false) String format) {
        return Telechargements.lettre(service.lettreDuCandidat(moi(), idOffre), "docx".equalsIgnoreCase(format));
    }

    /** 201 ; 400 {@code QUESTION_OBLIGATOIRE} ; 409 {@code NON_INFORME}, {@code OFFRE_RETENUE}. */
    @PostMapping("/explication")
    public ResponseEntity<AttributionDto.Explication> demander(@PathVariable String idOffre,
            @RequestBody(required = false) AttributionDto.ExplicationRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.demanderExplication(moi(), idOffre, r));
    }

    @GetMapping("/explications")
    public List<AttributionDto.Explication> explications(@PathVariable String idOffre) {
        return service.explicationsDuCandidat(moi(), idOffre);
    }

    @GetMapping("/explications/{id}/fichier")
    public ResponseEntity<byte[]> fichier(@PathVariable String idOffre, @PathVariable Long id) {
        AttributionExplication e = service.fichierExplicationDuCandidat(moi(), idOffre, id);
        return Telechargements.fichier(e.getReponseNom(), e.getReponseFormat(), e.getReponseContenu());
    }

    /**
     * ⚠️ 2c (§B5, art. 20-I) — l'attributaire dépose sa pièce fiscale ou sociale : multipart {@code type} (FISCALE | SOCIALE),
     * {@code dateDelivrance}, {@code fichier} ; 201 ; 400 {@code PIECE_PERIMEE} ; 409 {@code NON_ATTRIBUTAIRE}, {@code DELAI_DEPASSE}.
     */
    @PostMapping(value = "/pieces-attributaire", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttributionDto.PiecesAttributaire> deposerPiece(@PathVariable String idOffre,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "dateDelivrance", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate dateDelivrance,
            @org.springframework.web.bind.annotation.RequestPart(value = "fichier", required = false) org.springframework.web.multipart.MultipartFile fichier) {
        return ResponseEntity.status(HttpStatus.CREATED).body(execution.deposerPiece(moi(), idOffre, type, dateDelivrance, fichier));
    }

    @GetMapping("/pieces-attributaire")
    public AttributionDto.PiecesAttributaire pieces(@PathVariable String idOffre) {
        return execution.piecesDuCandidat(moi(), idOffre);
    }

    @GetMapping("/pieces-attributaire/{id}/fichier")
    public ResponseEntity<byte[]> fichierPiece(@PathVariable String idOffre, @PathVariable Long id) {
        cnm.prs.entity.AttributionPiece p = execution.fichierDuCandidat(moi(), idOffre, id);
        return Telechargements.fichier(p.getNom(), p.getFormat(), p.getContenu());
    }

    /** ⚠️ 2c — le marché signé ; le lire après la notification vaut accusé de réception (date d'effet). 404 avant la signature. */
    @GetMapping("/marche")
    public ResponseEntity<byte[]> marche(@PathVariable String idOffre) {
        cnm.prs.entity.AttributionPiece p = execution.marcheDuCandidat(moi(), idOffre);
        return Telechargements.fichier(p.getNom(), p.getFormat(), p.getContenu());
    }

    private static String moi() {
        return CurrentUser.ref().orElseThrow(() -> new ResourceNotFoundException("Compte candidat introuvable."));
    }
}
