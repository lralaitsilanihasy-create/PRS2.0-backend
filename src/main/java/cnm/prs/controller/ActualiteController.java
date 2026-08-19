package cnm.prs.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;

import cnm.prs.dto.ActualiteDto;
import cnm.prs.dto.ActualiteImageDto;
import cnm.prs.entity.ActualiteImage;
import cnm.prs.service.ActualiteService;

/**
 * Contrôleur REST des actualités d'ouverture de session (spec du 2026-08-18).
 *
 * <p>Administration réservée à {@code ADMINISTRATEUR} ; {@code /mes-actualites} et la lecture
 * d'image sont ouvertes à tout utilisateur authentifié (le filtrage de visibilité est
 * entièrement serveur). Le DELETE <strong>archive</strong>, il ne supprime jamais.</p>
 */
@RestController
@RequestMapping("/api/actualites")
public class ActualiteController {

    private final ActualiteService service;

    public ActualiteController(ActualiteService service) {
        this.service = service;
    }

    /** Modal d'ouverture de session : actualités visibles pour le profil authentifié (JWT/cookie). */
    @GetMapping("/mes-actualites")
    public List<ActualiteDto> mesActualites() {
        return service.mesActualites();
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @GetMapping(params = "page")
    public Page<ActualiteDto> findAllPagine(Pageable pageable) {
        return service.findAllPagine(pageable);
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @GetMapping
    public List<ActualiteDto> findAll() {
        return service.findAll();
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @GetMapping("/{id}")
    public ActualiteDto findById(@PathVariable Integer id) {
        return service.findById(id);
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PostMapping
    public ResponseEntity<ActualiteDto> create(@Valid @RequestBody ActualiteDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PutMapping("/{id}")
    public ActualiteDto update(@PathVariable Integer id, @Valid @RequestBody ActualiteDto dto) {
        return service.update(id, dto);
    }

    /** Archivage logique (onglet « Historique ») — jamais de suppression physique. */
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archiver(@PathVariable Integer id) {
        service.archiver(id);
        return ResponseEntity.noContent().build();
    }

    /** Ajout d'image : multipart, partie {@code fichier} — JPEG (magic-bytes) ≤ 10 Mo, redimensionnée serveur. */
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ActualiteImageDto> ajouterImage(@PathVariable Integer id,
            @RequestPart(value = "fichier", required = false) MultipartFile fichier) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.ajouterImage(id, fichier));
    }

    /** Lecture d'une image de la mini-page (tout authentifié) — sortie durcie, jamais de MIME client. */
    @GetMapping("/{id}/images/{idImage}")
    public ResponseEntity<byte[]> image(@PathVariable Integer id, @PathVariable Integer idImage) {
        ActualiteImage img = service.image(id, idImage);
        String nom = img.getNomFichier() != null ? img.getNomFichier() : "actualite-" + id + "-" + idImage + ".jpg";
        return ResponseEntity.ok()
                .contentType(Telechargements.typeAutorise(img.getFormat()))
                .header(HttpHeaders.CONTENT_DISPOSITION, Telechargements.disposition(nom))
                .body(img.getContenu());
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @DeleteMapping("/{id}/images/{idImage}")
    public ResponseEntity<Void> supprimerImage(@PathVariable Integer id, @PathVariable Integer idImage) {
        service.supprimerImage(id, idImage);
        return ResponseEntity.noContent().build();
    }
}
