package cnm.prs.controller;

import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.EntrepriseCandidatDto;
import cnm.prs.entity.PieceEntreprise;
import cnm.prs.service.EntrepriseCandidatService;
import jakarta.validation.Valid;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 1b, §B4) — l'écran de l'<strong>Administrateur</strong> pour
 * la vérification du NIF sur pièces : les entreprises d'un statut (les plus anciennes d'abord), la décision, le fichier
 * d'une pièce.
 */
@RestController
@RequestMapping("/api/admin/entreprises")
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class AdminEntrepriseController {

    private final EntrepriseCandidatService service;

    public AdminEntrepriseController(EntrepriseCandidatService service) {
        this.service = service;
    }

    /** {@code ?verification=NON_VERIFIE} par défaut. */
    @GetMapping
    public List<EntrepriseCandidatDto.Entreprise> lister(@RequestParam(defaultValue = "NON_VERIFIE") String verification) {
        return service.parVerification(verification);
    }

    /** {@code { statut: VERIFIE_SUR_PIECES | REFUSE_SUR_PIECES, motif }} ; 400 (motif obligatoire pour un refus) ; 404. */
    @PostMapping("/{id}/verification")
    public EntrepriseCandidatDto.Verification decider(@PathVariable Integer id, @Valid @RequestBody EntrepriseCandidatDto.Decision corps) {
        return service.decider(id, corps);
    }

    @GetMapping("/{id}/pieces/{idPiece}/fichier")
    public ResponseEntity<byte[]> fichier(@PathVariable Integer id, @PathVariable Integer idPiece) {
        PieceEntreprise p = service.fichier(id, idPiece);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(p.getFormat()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(p.getNomFichier() == null ? "piece-" + p.getIdPiece() : p.getNomFichier(),
                                java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .body(p.getContenu());
    }
}
