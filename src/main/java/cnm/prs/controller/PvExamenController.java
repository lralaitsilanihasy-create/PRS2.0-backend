package cnm.prs.controller;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.security.access.prepost.PreAuthorize;

import jakarta.validation.Valid;

import cnm.prs.dto.PvActionRequest;
import cnm.prs.dto.PvExamenDto;
import cnm.prs.service.PvExamenService;

/**
 * Contrôleur REST pour la ressource {@code pv-examens} (table {@code t_pv_examen}).
 */
@RestController
@RequestMapping("/api/pv-examens")
public class PvExamenController {

    private final PvExamenService service;

    public PvExamenController(PvExamenService service) {
        this.service = service;
    }

    // Projets de PV (non signés). Les PV signés sont exposés par GET /api/pv-examens/definitifs.
    @GetMapping
    public List<PvExamenDto> projets() {
        return service.projets();
    }

    /** PV définitifs (signés) — liste séparée des projets de PV. */
    @GetMapping("/definitifs")
    public List<PvExamenDto> definitifs() {
        return service.definitifs();
    }

    @GetMapping("/{id}")
    public PvExamenDto findById(@PathVariable Integer id) {
        return service.findById(id);
    }

    /**
     * Document PDF du Projet de PV (généré à la soumission si éligible). Authentifié, dans le périmètre.
     * ⚠️ Règle ajoutée (2026-08-01) — le fichier porte la RÉFÉRENCE du PV (`refePv`, repli `referencePv`),
     * caractères interdits remplacés par « - » (ex. {@code 00020-PPM-CRM-ANT-PV-2026.pdf}).
     */
    @GetMapping("/{id}/document")
    public ResponseEntity<byte[]> document(@PathVariable Integer id) {
        byte[] pdf = service.telechargerDocument(id);
        PvExamenDto pv = service.findById(id);
        String ref = pv.getRefePv() != null && !pv.getRefePv().isBlank() ? pv.getRefePv() : pv.getReferencePv();
        String nom = (ref == null || ref.isBlank() ? "pv-" + id : ref.replaceAll("[\\\\/:*?\"<>|]", "-")) + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nom + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // Rédaction / édition du projet de PV : Membre (rédacteur), CC ou Président (§3.5).
    @PreAuthorize("@perm.peutExercer('MEMBRE')")
    @PostMapping
    public ResponseEntity<PvExamenDto> create(@Valid @RequestBody PvExamenDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PreAuthorize("@perm.peutExercer('MEMBRE')")
    @PutMapping("/{id}")
    public PvExamenDto update(@PathVariable Integer id, @Valid @RequestBody PvExamenDto dto) {
        return service.update(id, dto);
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ----------------------------------------------------------------------
    // Transitions du circuit de contrôle (workflow §2, §3.2, §3.5)
    // ----------------------------------------------------------------------

    /** Soumission du projet par le Membre : → PROJET_SOUMIS. */
    @PreAuthorize("@perm.peutExercer('MEMBRE')")
    @PostMapping("/{id}/soumettre")
    public PvExamenDto soumettre(@PathVariable Integer id, @Valid @RequestBody PvActionRequest req) {
        return service.soumettre(id, req);
    }

    /** Retour pour rectification par le Président / CC : → EN_RECTIFICATION (commentaire obligatoire). */
    @PreAuthorize("@perm.peutExercer('CHEF_COMMISSION')")
    @PostMapping("/{id}/retourner")
    public PvExamenDto retourner(@PathVariable Integer id, @Valid @RequestBody PvActionRequest req) {
        return service.retourner(id, req);
    }

    /** Acceptation du projet par le Président / CC : → PROJET_ACCEPTE. */
    @PreAuthorize("@perm.peutExercer('CHEF_COMMISSION')")
    @PostMapping("/{id}/accepter")
    public PvExamenDto accepter(@PathVariable Integer id, @Valid @RequestBody PvActionRequest req) {
        return service.accepter(id, req);
    }

    /** Co-signature du PV (rôle MEMBRE / PRESIDENT / CC) : → SIGNE quand les deux camps ont signé. */
    @PreAuthorize("@perm.peutExercer('MEMBRE')")
    @PostMapping("/{id}/signer")
    public PvExamenDto signer(@PathVariable Integer id, @Valid @RequestBody PvActionRequest req) {
        return service.signer(id, req);
    }

    /**
     * ⚠️ Spec navette (2026-08-01) — ARCHIVAGE du PV par l'Assistant contrôleur (après transmission
     * SIGMP) : pose la date d'archivage et CLÔT le dossier.
     */
    @PreAuthorize("@perm.peutExercer('ASSISTANT_CONTROLEUR')")
    @PostMapping("/{id}/archiver")
    public PvExamenDto archiver(@PathVariable Integer id) {
        return service.archiver(id);
    }
}
