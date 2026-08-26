package cnm.prs.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.DmcDto;
import cnm.prs.service.DmcService;

/**
 * Contrôleur REST pour la ressource {@code dmcs} (table {@code t_dossier_mec}) : dossier de mise en
 * concurrence, un par ligne de marché, de type dérivé du mode de passation.
 *
 * <p>⚠️ LOT 3a (2026-08-26) — §1/§3.1. La ressource <strong>est rattachée à un dossier</strong> (par
 * sa ligne de marché) : la <strong>lecture</strong> reste ouverte à tout authentifié mais est scopée
 * dans le service au périmètre de ce dossier (403 hors périmètre). La <strong>création</strong> passe
 * à l'Administrateur seul : aucun écran du frontend n'appelle {@code /api/dmcs} — le DMC est une
 * préparation déclenchée explicitement (le reste du cycle de vie, re-dérivation du type et
 * suppression en cascade, est piloté en interne par {@code MarcheService}, hors de ce contrôleur).</p>
 */
@RestController
@RequestMapping("/api/dmcs")
public class DmcController {

    private final DmcService service;

    public DmcController(DmcService service) {
        this.service = service;
    }

    /**
     * Crée le DMC d'une ligne de marché (type dérivé du mode). 400 si mode non mappé, 409 si déjà créé.
     *
     * <p>⚠️ LOT 3a (2026-08-26) — réservé à l'Administrateur (voir en-tête).</p>
     */
    @PostMapping("/par-marche/{idDetail}")
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public ResponseEntity<DmcDto> creer(@PathVariable Integer idDetail) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creerPourMarche(idDetail));
    }

    /** DMC d'une ligne de marché (404 si aucun). */
    @GetMapping("/par-marche/{idDetail}")
    public DmcDto findByMarche(@PathVariable Integer idDetail) {
        return service.findByMarche(idDetail);
    }

    @GetMapping("/{id}")
    public DmcDto findById(@PathVariable Long id) {
        return service.findById(id);
    }
}
