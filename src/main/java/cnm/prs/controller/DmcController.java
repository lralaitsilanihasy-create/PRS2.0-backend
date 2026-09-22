package cnm.prs.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.DmcDto;
import cnm.prs.dto.LigneEligibleDto;
import cnm.prs.service.DmcService;

/**
 * Contrôleur REST pour la ressource {@code dmcs} (table {@code t_dossier_mec}) : dossier de mise en
 * concurrence, un par ligne de marché, de type dérivé du mode de passation.
 *
 * <p>⚠️ LOT 3a (2026-08-26) — §1/§3.1. La ressource <strong>est rattachée à un dossier</strong> (par
 * sa ligne de marché) : la <strong>lecture</strong> reste ouverte à tout authentifié mais est scopée
 * dans le service au périmètre de ce dossier (403 hors périmètre).</p>
 *
 * <p>⚠️ Fiche marché DAO (demande front du 2026-09-22, §B2) — la <strong>création</strong> s'ouvre à la PRMP et à
 * son UGPM (gardes H4 dans le service, 409 à code stable) ; l'Administrateur garde son geste d'origine.
 * {@code GET /eligibles} liste les lignes candidates — chemin littéral déclaré <em>avant</em> {@code /{id}}, qui
 * l'attrapait en 400.</p>
 */
@RestController
@RequestMapping("/api/dmcs")
public class DmcController {

    private final DmcService service;

    public DmcController(DmcService service) {
        this.service = service;
    }

    /** Lignes de PPM éligibles à un appel d'offres pour l'utilisateur courant (H4). */
    @GetMapping("/eligibles")
    public List<LigneEligibleDto> eligibles() {
        return service.eligibles();
    }

    /**
     * Crée le DMC d'une ligne de marché (type dérivé du mode). Administrateur : 400 si mode non mappé, 409 si déjà
     * créé. PRMP / UGPM : 403 hors de ses plans, 409 nominatifs H4 ({@code LIGNE_RETIREE}, {@code MODE_NON_DAO},
     * {@code PV_NON_SIGNE}, {@code DAO_EXISTANT}, {@code VACANCE_PRMP}).
     */
    @PostMapping("/par-marche/{idDetail}")
    @PreAuthorize("hasAnyRole('ADMINISTRATEUR', 'PRMP', 'UGPM')")
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
