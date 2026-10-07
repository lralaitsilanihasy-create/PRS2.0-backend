package cnm.prs.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.AmiDto;
import cnm.prs.entity.AmiExpressionPiece;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.security.CurrentUser;
import cnm.prs.service.AmiService;

/**
 * ⚠️ 2026-10-07 (AMI en ligne, tranche AMI-a, §B1, §B2 ; V82) — les AMI publiés, lus sans session ({@code /api/amis-en-ligne}), et le
 * dépôt de l'expression d'intérêt par le candidat connecté ({@code /api/candidat/amis/{idDmc}/expression}).
 */
@RestController
public class AmisEnLigneController {

    private final AmiService service;

    public AmisEnLigneController(AmiService service) {
        this.service = service;
    }

    @GetMapping("/api/amis-en-ligne")
    public List<AmiDto.AmiPublic> lister() {
        return service.publics();
    }

    @GetMapping("/api/amis-en-ligne/{idDmc}")
    public AmiDto.AmiPublic lire(@PathVariable Long idDmc) {
        return service.publicDe(idDmc);
    }

    @GetMapping("/api/amis-en-ligne/{idDmc}/avis")
    public ResponseEntity<byte[]> avis(@PathVariable Long idDmc, @RequestParam(required = false) String format) {
        boolean docx = "docx".equalsIgnoreCase(format);
        return AmiController.reponse(idDmc, docx, service.avisPublic(idDmc, docx));
    }

    /** 201 ; multipart {@code expression} (JSON) + {@code fichiers} ; 400 {@code PIECES_MANQUANTES} ; 409 {@code DATE_LIMITE_DEPASSEE}. */
    @PostMapping(value = "/api/candidat/amis/{idDmc}/expression", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CANDIDAT')")
    public ResponseEntity<AmiDto.Expression> deposer(@PathVariable Long idDmc, @RequestParam(value = "expression", required = false) String expression,
            @RequestPart(value = "fichiers", required = false) List<MultipartFile> fichiers) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.deposer(moi(), idDmc, expression, fichiers));
    }

    @GetMapping("/api/candidat/amis/{idDmc}/expression")
    @PreAuthorize("hasRole('CANDIDAT')")
    public AmiDto.Expression sienne(@PathVariable Long idDmc) {
        return service.sienne(moi(), idDmc);
    }

    /** 204 ; 409 {@code DATE_LIMITE_DEPASSEE}. */
    @DeleteMapping("/api/candidat/amis/{idDmc}/expression")
    @PreAuthorize("hasRole('CANDIDAT')")
    public ResponseEntity<Void> retirer(@PathVariable Long idDmc) {
        service.retirer(moi(), idDmc);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/candidat/amis/{idDmc}/expression/pieces/{idPiece}")
    @PreAuthorize("hasRole('CANDIDAT')")
    public ResponseEntity<byte[]> piece(@PathVariable Long idDmc, @PathVariable Long idPiece) {
        AmiExpressionPiece p = service.pieceDuCandidat(moi(), idDmc, idPiece);
        return Telechargements.fichier(p.getNom(), p.getFormat(), p.getContenu());
    }

    private static String moi() {
        return CurrentUser.ref().orElseThrow(() -> new ResourceNotFoundException("Compte candidat introuvable."));
    }
}
