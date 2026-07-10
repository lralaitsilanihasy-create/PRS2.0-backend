package cnm.prs.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.DmcDto;
import cnm.prs.service.DmcService;

/**
 * Contrôleur REST pour la ressource {@code dmcs} (table {@code t_dossier_mec}) : dossier de mise en
 * concurrence, un par ligne de marché, de type dérivé du mode de passation. Réservé aux utilisateurs
 * authentifiés (opération de préparation de dossier).
 */
@RestController
@RequestMapping("/api/dmcs")
public class DmcController {

    private final DmcService service;

    public DmcController(DmcService service) {
        this.service = service;
    }

    /** Crée le DMC d'une ligne de marché (type dérivé du mode). 400 si mode non mappé, 409 si déjà créé. */
    @PostMapping("/par-marche/{idDetail}")
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
