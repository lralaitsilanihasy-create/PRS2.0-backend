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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import cnm.prs.dto.LettreRenvoiDto;
import cnm.prs.service.LettreRenvoiService;

/**
 * Contrôleur REST pour la ressource {@code lettre-renvois} (table {@code t_lettre_renvoi}).
 * Lecture : authentifié (filtré localité). Édition/soumission/signature : Président ou CC
 * (⚠️ règle modifiée 2026-08-01 — la lettre appartient à la clôture de navette, plus au Membre).
 */
@RestController
@RequestMapping("/api/lettre-renvois")
public class LettreRenvoiController {

    private final LettreRenvoiService service;

    public LettreRenvoiController(LettreRenvoiService service) {
        this.service = service;
    }

    @GetMapping
    public List<LettreRenvoiDto> findAll() {
        return service.findAll();
    }

    /** Lettres de renvoi signées concernant les dossiers de la PRMP connectée (lecture seule). */
    @PreAuthorize("hasRole('PRMP')")
    @GetMapping("/mes-lettres")
    public List<LettreRenvoiDto> mesLettres() {
        return service.mesLettres();
    }

    @GetMapping("/{id}")
    public LettreRenvoiDto findById(@PathVariable Integer id) {
        return service.findById(id);
    }

    /** Document PDF de la lettre signée (généré à la signature). Authentifié, dans le périmètre. */
    @GetMapping("/{id}/document")
    public ResponseEntity<byte[]> document(@PathVariable Integer id) {
        byte[] pdf = service.telechargerDocument(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"lettre-renvoi-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    /** Création d'une lettre de renvoi (statut BROUILLON) — clôture de navette, Président/CC. */
    @PreAuthorize("@perm.peutExercer('CHEF_COMMISSION')")
    @PostMapping
    public ResponseEntity<LettreRenvoiDto> create(@Valid @RequestBody LettreRenvoiDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PreAuthorize("@perm.peutExercer('CHEF_COMMISSION')")
    @PutMapping("/{id}")
    public LettreRenvoiDto update(@PathVariable Integer id, @Valid @RequestBody LettreRenvoiDto dto) {
        return service.update(id, dto);
    }

    @PreAuthorize("@perm.peutExercer('CHEF_COMMISSION')")
    @PostMapping("/{id}/soumettre")
    public LettreRenvoiDto soumettre(@PathVariable Integer id) {
        return service.soumettre(id);
    }

    @PreAuthorize("@perm.peutExercer('CHEF_COMMISSION')")
    @PostMapping("/{id}/signer")
    public LettreRenvoiDto signer(@PathVariable Integer id) {
        return service.signer(id);
    }

    /** ⚠️ Spec navette (2026-08-01) — archivage de la lettre signée par l'Assistant contrôleur. */
    @PreAuthorize("@perm.peutExercer('ASSISTANT_CONTROLEUR')")
    @PostMapping("/{id}/archiver")
    public LettreRenvoiDto archiver(@PathVariable Integer id) {
        return service.archiver(id);
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
