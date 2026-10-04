package cnm.prs.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.OffreDto;
import cnm.prs.service.OffreService;
import cnm.prs.service.ParametreService;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 3, §B1, §B5) — l'horloge du serveur (publique) et les dépôts d'une
 * procédure (PRMP et UGPM de la fiche, responsable de la procédure : gardes dans le service).
 */
@RestController
public class DepotsController {

    private final OffreService service;
    private final ParametreService parametres;

    public DepotsController(OffreService service, ParametreService parametres) {
        this.service = service;
        this.parametres = parametres;
    }

    /** {@code { maintenant: 'AAAA-MM-JJTHH:MM:SS', fuseau }} : le temps restant se compte d'après le serveur, jamais le poste. */
    @GetMapping("/api/horloge")
    public OffreDto.Horloge horloge() {
        return service.horloge(parametres.remiseElectronique().fuseau());
    }

    /** Avant la date limite, le nombre seul ; après, le registre des dépôts. */
    @GetMapping("/api/fiches-marche/{idDmc}/depots")
    public OffreDto.Depots depots(@PathVariable Long idDmc) {
        return service.depots(idDmc);
    }
}
