package cnm.prs.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
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

import cnm.prs.dto.LotDto;
import cnm.prs.service.LotService;

/**
 * Contrôleur REST pour la ressource {@code lots} (table {@code t_lot}).
 *
 * <p>⚠️ LOT 3a (2026-08-26) — §1/§3.1. <strong>Lecture</strong> : ouverte à tout authentifié mais
 * <strong>scopée dans le service</strong> au périmètre du dossier parent (Président/Administrateur :
 * tout ; contrôleurs : leur localité ; PRMP/UGPM : leurs dossiers) — 403 sur un accès unitaire hors
 * périmètre. <strong>Écriture</strong> : PRMP, UGPM (tutelle) et Administrateur uniquement, et le
 * service exige en plus un dossier parent en {@code BROUILLON} dont l'appelant est propriétaire
 * (403 propriétaire / 409 pas brouillon) — c'est le flux réel du modal d'édition d'un brouillon.</p>
 */
@RestController
@RequestMapping("/api/lots")
public class LotController {

    private final LotService service;

    public LotController(LotService service) {
        this.service = service;
    }

    @GetMapping
    public List<LotDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public LotDto findById(@PathVariable Integer id) {
        return service.findById(id);
    }

    /** Lots d'une ligne de marché (liste, vide si aucun ; pas de 404). */
    @GetMapping("/par-marche/{idDetail}")
    public List<LotDto> findByMarche(@PathVariable Integer idDetail) {
        return service.findByMarche(idDetail);
    }

    /** Tous les lots d'un dossier (liste, vide si aucun ; pas de 404). */
    @GetMapping("/par-dossier/{idDossier}")
    public List<LotDto> findByDossier(@PathVariable Integer idDossier) {
        return service.findByDossier(idDossier);
    }

    /** ⚠️ LOT 3a (2026-08-26) — §3.1 : écriture réservée à la PRMP/UGPM propriétaire d'un brouillon (ou Admin). */
    @PostMapping
    @PreAuthorize("hasAnyRole('PRMP','UGPM','ADMINISTRATEUR')")
    public ResponseEntity<LotDto> create(@Valid @RequestBody LotDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    /** ⚠️ LOT 3a (2026-08-26) — §3.1 : idem création (dossier parent BROUILLON et propriété de l'appelant). */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PRMP','UGPM','ADMINISTRATEUR')")
    public LotDto update(@PathVariable Integer id, @Valid @RequestBody LotDto dto) {
        return service.update(id, dto);
    }

    /** ⚠️ LOT 3a (2026-08-26) — §3.1 : idem création. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('PRMP','UGPM','ADMINISTRATEUR')")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
