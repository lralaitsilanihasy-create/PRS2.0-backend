package cnm.prs.controller;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import cnm.prs.dto.InscriptionEnAttenteDto;
import cnm.prs.dto.RefusInscriptionRequest;
import cnm.prs.dto.ValidationInscriptionRequest;
import cnm.prs.dto.ValidationInscriptionResponse;
import cnm.prs.entity.PieceJointe;
import cnm.prs.enums.TypePieceJointe;
import cnm.prs.service.InscriptionService;

/**
 * Instruction des inscriptions PRMP par l'Administrateur (§3.1) : liste des inscriptions en
 * attente, validation (partielle) ou refus motivé, et téléchargement des pièces.
 *
 * <p>Écriture et consultation réservées à l'Administrateur ; le <strong>téléchargement d'une
 * pièce</strong> est ouvert à l'Administrateur <em>ou</em> au propriétaire de l'inscription.</p>
 */
@RestController
@RequestMapping("/api/inscriptions")
public class InscriptionController {

    private final InscriptionService service;

    public InscriptionController(InscriptionService service) {
        this.service = service;
    }

    @GetMapping("/en-attente")
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public List<InscriptionEnAttenteDto> enAttente() {
        return service.enAttente();
    }

    /** Validation (partielle) : active les entités disponibles, crée les entités proposées acceptées. */
    @PostMapping("/{login}/valider")
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public ValidationInscriptionResponse valider(@PathVariable String login,
            @Valid @RequestBody(required = false) ValidationInscriptionRequest request) {
        return service.valider(login, request);
    }

    /** Refus motivé : le compte reste non connectable ; la PRMP est notifiée du motif. */
    @PostMapping("/{login}/refuser")
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public ResponseEntity<Void> refuser(@PathVariable String login,
            @Valid @RequestBody RefusInscriptionRequest request) {
        service.refuser(login, request.motif());
        return ResponseEntity.noContent().build();
    }

    /** Téléchargement d'une pièce (Administrateur ou propriétaire de l'inscription). */
    @GetMapping("/{login}/pieces/{type}")
    @PreAuthorize("hasRole('ADMINISTRATEUR') or #login == authentication.name")
    public ResponseEntity<byte[]> telecharger(@PathVariable String login, @PathVariable TypePieceJointe type) {
        PieceJointe piece = service.telecharger(login, type);
        // ⚠️ Audit front (2026-08-16) — type de sortie sur LISTE BLANCHE (jamais le format stocké tel
        // quel : un HTML téléversé ne doit pas sortir en text/html) + nom d'en-tête assaini.
        String nom = piece.getLibelle() != null ? piece.getLibelle() : login + "_" + type;
        return ResponseEntity.ok()
                .contentType(Telechargements.typeAutorise(piece.getFormat()))
                .header(HttpHeaders.CONTENT_DISPOSITION, Telechargements.disposition(nom))
                .body(piece.getContenu());
    }
}
