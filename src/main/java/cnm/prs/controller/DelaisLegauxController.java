package cnm.prs.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.DelaiLegalDto;
import cnm.prs.dto.DelaiParametreDto;
import cnm.prs.service.DelaiParametreService;
import cnm.prs.service.DelaisLegaux;

/**
 * ⚠️ R6a (manuel révisé pour l'application, lot 2, §B1 ; V106, 2026-10-13) — les neuf délais de la loi (en code, lecture seule) et les
 * délais de publicité administrables (lecture, réglage par l'Administrateur).
 */
@RestController
@RequestMapping("/api")
public class DelaisLegauxController {

    private final DelaiParametreService parametres;

    public DelaisLegauxController(DelaiParametreService parametres) {
        this.parametres = parametres;
    }

    /** Les neuf délais légaux, dans l'ordre de la table du pilote. */
    @GetMapping("/delais-legaux")
    public List<DelaiLegalDto> delaisLegaux() {
        return DelaisLegaux.TABLE;
    }

    /** Les délais administrables (signalés, non bloquants). */
    @GetMapping("/delais-parametres")
    public List<DelaiParametreDto> delaisParametres() {
        return parametres.tableau();
    }

    /** Réglage d'un délai administrable (Administrateur) : { valeur, unite, texte, actif }, un champ absent garde sa valeur. */
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PutMapping("/delais-parametres/{code}")
    public DelaiParametreDto definir(@PathVariable String code, @RequestBody(required = false) DelaiParametreDto.Request corps) {
        return parametres.definir(code, corps);
    }
}
