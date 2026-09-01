package cnm.prs.controller;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
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
import cnm.prs.dto.PvVisaRequest;
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

    /**
     * ⚠️ RETIRÉ le 2026-08-31 — <strong>410 Gone</strong>. L'acceptation est fusionnée dans le VISA.
     * Conservé plutôt que supprimé : la livraison se faisant « backend d'abord », un front pas encore
     * aligné appellera encore ce chemin, et un 410 nommant son remplaçant se diagnostique là où un 404
     * enverrait chercher une faute de frappe.
     */
    @PreAuthorize("@perm.peutExercer('CHEF_COMMISSION')")
    @PostMapping("/{id}/accepter")
    public PvExamenDto accepter(@PathVariable Integer id, @Valid @RequestBody PvActionRequest req) {
        return service.accepter(id, req);
    }

    /**
     * ⚠️ <strong>VISA</strong> (2026-08-31) — clôture de la navette en un seul geste : avis
     * éventuellement modifié, Secrétaire de séance, Membre co-signataire et part de signature du rôle.
     * Remplace {@code accepter} + {@code signer(role=PRESIDENT|CC)}.
     *
     * <p>Pas de champ {@code role} : la part signée est dérivée du profil de l'acteur. L'habilitation
     * fine — <strong>seul le dispatcheur vise</strong> — est en service : elle porte sur l'IDENTITÉ,
     * que {@code @PreAuthorize} ne sait pas exprimer (une paire de délégation active satisferait la
     * garde de profil sans donner le droit de viser).</p>
     */
    @PreAuthorize("@perm.peutExercer('CHEF_COMMISSION')")
    @PostMapping(value = "/{id}/viser", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PvExamenDto viser(@PathVariable Integer id, @Valid @RequestBody PvVisaRequest req) {
        return service.viser(id, req);
    }

    /**
     * ⚠️ <strong>VISA PAR INTÉRIM</strong> (2026-09-01) — même geste, en multipart, avec la
     * <strong>note d'intérim</strong> qui justifie l'absence du dispatcheur.
     *
     * <p>Deux mappages sur le même chemin, distingués par {@code consumes} : le chemin normal reste en
     * JSON pur, strictement inchangé. Le front n'envoie du multipart que lorsqu'il sait l'acteur non
     * dispatcheur — il le sait par {@code imDispatcheur} du DTO.</p>
     *
     * <p>Parties : <strong>{@code data}</strong> (le {@link PvVisaRequest} en JSON, identique au chemin
     * normal) et <strong>{@code noteInterim}</strong> (le PDF). Même convention que le dépôt de pièces
     * jointes, à ceci près que la partie fichier porte ici un nom explicite plutôt que « fichier » :
     * une requête de visa peut un jour en transporter d'autres.</p>
     *
     * <p>Un dispatcheur qui enverrait quand même une note obtient le visa normal : la note est ignorée,
     * elle n'a rien à justifier. L'inverse — un non-dispatcheur sans note — est un <strong>400</strong>.</p>
     */
    @PreAuthorize("@perm.peutExercer('CHEF_COMMISSION')")
    @PostMapping(value = "/{id}/viser", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PvExamenDto viserParInterim(@PathVariable Integer id,
            @Valid @RequestPart("data") PvVisaRequest req,
            @RequestPart(value = "noteInterim", required = false) MultipartFile noteInterim) {
        return service.viser(id, req, noteInterim);
    }

    /**
     * ⚠️ Note d'intérim (2026-09-01) — PDF justifiant l'absence du dispatcheur.
     *
     * <p>Accès <strong>plus étroit que le PV</strong> : contrôleurs du périmètre et Administrateur, la
     * PRMP en est exclue (contrôlé en service). L'arbitrage du 01/09 retire toute mention d'intérim du
     * PV central pour que l'extérieur ne l'apprenne pas ; ouvrir la note à la PRMP le rétablirait par
     * une autre porte.</p>
     */
    @GetMapping("/{id}/note-interim")
    public ResponseEntity<byte[]> noteInterim(@PathVariable Integer id) {
        byte[] pdf = service.telechargerNoteInterim(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"note-interim-" + id + ".pdf\"")
                .body(pdf);
    }

    /** Co-signature du PV — ⚠️ depuis le 2026-08-31, rôle MEMBRE seul : → SIGNE (la part P/CC vient du visa). */
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
