package cnm.prs.controller;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;

import cnm.prs.dto.PieceJointeDossierDto;
import cnm.prs.entity.PieceJointeDossier;
import cnm.prs.service.PieceJointeDossierService;

/**
 * Contrôleur REST pour la ressource {@code piece-jointe-dossiers} (table {@code t_piece_jointe_dossier}).
 * Upload multipart (part {@code data} JSON + part {@code fichier}) réservé à la PRMP propriétaire ;
 * suppression à la PRMP (dossier BROUILLON) ou à l'Administrateur.
 */
@RestController
@RequestMapping("/api/piece-jointe-dossiers")
public class PieceJointeDossierController {

    private final PieceJointeDossierService service;

    public PieceJointeDossierController(PieceJointeDossierService service) {
        this.service = service;
    }

    /** Pièces d'un dossier. */
    @GetMapping
    public List<PieceJointeDossierDto> findByDossier(@RequestParam("dossier") Integer dossier) {
        return service.findByDossier(dossier);
    }

    @GetMapping("/{id}")
    public PieceJointeDossierDto findById(@PathVariable Integer id) {
        return service.findById(id);
    }

    /** Téléchargement du contenu binaire de la pièce. */
    @GetMapping("/{id}/contenu")
    public ResponseEntity<byte[]> contenu(@PathVariable Integer id) {
        PieceJointeDossier piece = service.telecharger(id);
        // ⚠️ Audit front (2026-08-16) — liste blanche de sortie + nom d'en-tête assaini (Telechargements).
        String nom = piece.getNomFichier() == null ? ("piece-" + id) : piece.getNomFichier();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, Telechargements.disposition(nom))
                .contentType(Telechargements.typeAutorise(piece.getFormat()))
                .body(piece.getContenu());
    }

    /** Upload d'une pièce (PRMP propriétaire). */
    @PreAuthorize("hasAnyRole('PRMP','UGPM')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PieceJointeDossierDto> upload(
            @Valid @RequestPart("data") PieceJointeDossierDto data,
            @RequestPart("fichier") MultipartFile fichier) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.store(data, fichier));
    }

    /** Suppression (PRMP propriétaire sur dossier BROUILLON, ou Administrateur). */
    @PreAuthorize("hasAnyRole('PRMP', 'UGPM', 'ADMINISTRATEUR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
