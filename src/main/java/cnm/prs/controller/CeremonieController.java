package cnm.prs.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.CeremonieDto;
import cnm.prs.entity.CleDetenteur;
import cnm.prs.service.CeremonieService;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 2, §B2, §B4, §B5 ; ADR-0013) — la <strong>cérémonie des
 * clés</strong> d'une procédure. Les gardes sont dans le service : le responsable de la procédure (tout), les membres
 * désignés (leur clé, leur part) ; 403 pour tout autre, Administrateur et PRMP compris.
 */
@RestController
@RequestMapping("/api/fiches-marche/{idDmc}/ceremonie")
public class CeremonieController {

    private final CeremonieService service;

    public CeremonieController(CeremonieService service) {
        this.service = service;
    }

    /** §B2.1 — l'état de la cérémonie et ses détenteurs (empreintes et clés publiques, jamais une enveloppe). */
    @GetMapping
    public CeremonieDto lire(@PathVariable Long idDmc) {
        return service.lire(idDmc);
    }

    /** §B2.2 — un membre publie sa clé : 201 ; 400 à code ; 409 {@code CEREMONIE_CLOSE} / {@code CLE_EXISTANTE}. */
    @PostMapping("/cles")
    public ResponseEntity<CeremonieDto.Detenteur> publier(@PathVariable Long idDmc, @RequestBody CeremonieDto.CleCorps corps) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.publier(idDmc, CleDetenteur.MEMBRE, corps));
    }

    /** §B5.1 — un membre remplace sa clé (cérémonie close ou non). */
    @PutMapping("/cles")
    public CeremonieDto.Detenteur remplacer(@PathVariable Long idDmc, @RequestBody CeremonieDto.CleCorps corps) {
        return service.remplacer(idDmc, CleDetenteur.MEMBRE, corps);
    }

    /** §B2.2 — l'enveloppe de sa propre clé : 403 à tout autre, responsable compris ; 404 sans clé. */
    @GetMapping("/cles/mienne")
    public CeremonieDto.Enveloppe mienne(@PathVariable Long idDmc) {
        return service.enveloppe(idDmc, CleDetenteur.MEMBRE);
    }

    /** ⚠️ 2026-10-05 (V71, §B2) — le <strong>dépositaire</strong> publie la part de secours (le responsable : 403 {@code GESTE_DU_DEPOSITAIRE}). */
    @PostMapping("/cles/secours")
    public ResponseEntity<CeremonieDto.Detenteur> publierSecours(@PathVariable Long idDmc, @RequestBody CeremonieDto.CleCorps corps) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.publier(idDmc, CleDetenteur.SECOURS, corps));
    }

    /** ⚠️ V71 — le dépositaire remplace la part de secours (S4 ; aussi un nouveau dépositaire, Q2) ; le responsable : 403 {@code GESTE_DU_DEPOSITAIRE}. */
    @PutMapping("/cles/secours")
    public CeremonieDto.Detenteur remplacerSecours(@PathVariable Long idDmc, @RequestBody CeremonieDto.CleCorps corps) {
        return service.remplacer(idDmc, CleDetenteur.SECOURS, corps);
    }

    /** L'enveloppe de la part de secours : son détenteur (⚠️ V71 — le dépositaire qui l'a publiée, ou le responsable pour l'ancien geste). */
    @GetMapping("/cles/secours")
    public CeremonieDto.Enveloppe secours(@PathVariable Long idDmc) {
        return service.enveloppe(idDmc, CleDetenteur.SECOURS);
    }

    /** §B4 — déclarer sa part perdue (membre), ou la part de secours ({@code ?role=SECOURS}, son détenteur : ⚠️ V71 le dépositaire, ou le responsable pour l'ancien geste). */
    @PostMapping("/cles/perdue")
    public CeremonieDto.Detenteur perdue(@PathVariable Long idDmc, @RequestParam(required = false) String role) {
        return service.perdue(idDmc, role);
    }

    /** §B2.4 — clore (responsable) : 409 {@code CLES_INCOMPLETES}. */
    @PostMapping("/cloturer")
    public CeremonieDto cloturer(@PathVariable Long idDmc) {
        return service.cloturer(idDmc);
    }

    /** §B5.2 — rouvrir (responsable) : 409 {@code DEPOT_EXISTANT}. */
    @PostMapping("/rouvrir")
    public CeremonieDto rouvrir(@PathVariable Long idDmc) {
        return service.rouvrir(idDmc);
    }

    /** §B4 (S2) — ouvrir un défi : 201 ; 409 {@code CLE_ABSENTE}. */
    @PostMapping("/defi")
    public ResponseEntity<CeremonieDto.Defi> defi(@PathVariable Long idDmc, @RequestParam(required = false) String role) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.defi(idDmc, role));
    }

    /** §B4 (S2) — répondre au défi : 409 {@code DEFI_EXPIRE} / {@code DEFI_ECHOUE}. */
    @PostMapping("/defi/{idDefi}")
    public CeremonieDto.Detenteur repondre(@PathVariable Long idDmc, @PathVariable Long idDefi,
            @RequestBody CeremonieDto.ReponseDefi corps) {
        return service.repondre(idDmc, idDefi, corps);
    }
}
