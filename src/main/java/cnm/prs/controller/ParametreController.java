package cnm.prs.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import cnm.prs.dto.InterrupteurDto;
import cnm.prs.dto.SeuilAgpmDto;
import cnm.prs.service.ParametreService;

/**
 * Paramètres système exposés à l'API ({@code t_parametre}) — pour l'instant, l'interrupteur
 * global des actualités (spec du 2026-08-18).
 */
@RestController
@RequestMapping("/api/parametres")
public class ParametreController {

    private final ParametreService service;

    public ParametreController(ParametreService service) {
        this.service = service;
    }

    /** État de l'interrupteur (tout authentifié — le front s'en sert pour l'écran Admin). */
    @GetMapping("/actualites-actives")
    public InterrupteurDto actualitesActives() {
        return new InterrupteurDto(service.actualitesActives());
    }

    /** Bascule de l'interrupteur global : coupe/rétablit le modal pour tous, d'un coup. */
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PutMapping("/actualites-actives")
    public InterrupteurDto basculer(@Valid @RequestBody InterrupteurDto corps) {
        return new InterrupteurDto(service.basculerActualites(corps.actif()));
    }

    /**
     * ⚠️ Arbitrage pilote (2026-09-07, suite) — seuil du déclenchement AGPM <strong>conditionnel</strong>
     * (appel à manifestation d'intérêt). Lecture ouverte à tout authentifié : l'écran d'administration
     * l'affiche, et le seuil explique pourquoi un plan est — ou n'est pas — en {@code PPM-AGPM}.
     */
    @GetMapping("/agpm-seuil-montant")
    public SeuilAgpmDto seuilAgpm() {
        return new SeuilAgpmDto(service.seuilAgpmMontant());
    }

    /** Réglage du seuil (Administrateur). Prend effet immédiatement, sans redéploiement. */
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PutMapping("/agpm-seuil-montant")
    public SeuilAgpmDto fixerSeuilAgpm(@Valid @RequestBody SeuilAgpmDto corps) {
        return new SeuilAgpmDto(service.fixerSeuilAgpmMontant(corps.seuil()));
    }

    /**
     * ⚠️ V45 (2026-09-25, formulaires du candidat, §B4) — le contrôle du taux de la garantie de soumission : taux de
     * référence et bornes (en %), {@code null} si non fixés. Lecture ouverte à tout authentifié.
     */
    @GetMapping("/fiche-garantie-taux")
    public ParametreService.TauxGarantie tauxGarantie() {
        return service.tauxGarantie();
    }

    /** Réglage (Administrateur) : l'état complet ; une valeur nulle efface le paramètre. */
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PutMapping("/fiche-garantie-taux")
    public ParametreService.TauxGarantie fixerTauxGarantie(@RequestBody ParametreService.TauxGarantie corps) {
        return service.fixerTauxGarantie(corps);
    }

    /**
     * ⚠️ V46 (2026-09-25, §B7) — le taux de TVA des bordereaux des prix générés, {@code {"taux": 20}} en %, {@code null}
     * si non fixé (le bordereau n'a alors pas de ligne TVA). Lecture ouverte à tout authentifié.
     */
    @GetMapping("/fiche-taux-tva")
    public ParametreService.TauxTva tauxTva() {
        return new ParametreService.TauxTva(service.tauxTva());
    }

    /** Réglage (Administrateur) ; {@code null} efface le taux. */
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PutMapping("/fiche-taux-tva")
    public ParametreService.TauxTva fixerTauxTva(@RequestBody ParametreService.TauxTva corps) {
        return service.fixerTauxTva(corps);
    }
}
