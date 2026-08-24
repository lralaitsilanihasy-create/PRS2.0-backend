package cnm.prs.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import cnm.prs.dto.DmcDto;
import cnm.prs.service.DmcService;

/**
 * Contrôleur REST pour la ressource {@code dmcs} (table {@code t_dossier_mec}) : dossier de mise en
 * concurrence, un par ligne de marché, de type dérivé du mode de passation.
 *
 * <p>⚠️ <strong>Réservé à l'{@code ADMINISTRATEUR}, lectures comprises.</strong> Aucun écran du front ne
 * consomme cette ressource (vérifié) : plutôt que de lui inventer un périmètre théorique, on la ferme au
 * plus strict tant qu'aucun usage réel ne la réclame. Le jour où un écran en a besoin, la garde s'ouvre en
 * une ligne — et se conçoit alors sur un besoin constaté. Le <strong>déclenchement interne</strong>
 * (création/re-dérivation/suppression depuis {@code MarcheService}) passe par le service, pas par cette
 * façade : il n'est pas concerné par cette garde.</p>
 */
@RestController
@RequestMapping("/api/dmcs")
@PreAuthorize("hasRole('ADMINISTRATEUR')")
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
