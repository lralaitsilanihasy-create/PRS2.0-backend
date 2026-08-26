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

import cnm.prs.dto.EcheanceDto;
import cnm.prs.service.EcheanceService;

/**
 * Contrôleur REST pour la ressource {@code echeances} (table {@code t_echeance}).
 *
 * <p>⚠️ LOT 3a (2026-08-26) — §1/§3.1 (Module 04 « Calendrier des jalons [Lecture] »).
 * <strong>Lecture</strong> ouverte à tout authentifié mais scopée dans le service au périmètre du
 * dossier parent (la PRMP consulte le calendrier de ses marchés, les contrôleurs celui de leur
 * localité). <strong>Écriture</strong> réservée à l'Administrateur : les jalons naissent des flux
 * internes (alertes J-7 / J-1), aucun profil métier ne les saisit à la main.</p>
 */
@RestController
@RequestMapping("/api/echeances")
public class EcheanceController {

    private final EcheanceService service;

    public EcheanceController(EcheanceService service) {
        this.service = service;
    }

    @GetMapping
    public List<EcheanceDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public EcheanceDto findById(@PathVariable Integer id) {
        return service.findById(id);
    }

    /** ⚠️ LOT 3a (2026-08-26) — §3.1 / Module 04 : écriture générique réservée à l'Administrateur. */
    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public ResponseEntity<EcheanceDto> create(@Valid @RequestBody EcheanceDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    /** ⚠️ LOT 3a (2026-08-26) — Administrateur seul (voir création). */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public EcheanceDto update(@PathVariable Integer id, @Valid @RequestBody EcheanceDto dto) {
        return service.update(id, dto);
    }

    /** ⚠️ LOT 3a (2026-08-26) — Administrateur seul (voir création). */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
