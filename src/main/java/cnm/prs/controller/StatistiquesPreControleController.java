package cnm.prs.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.StatistiquesReglesDto;
import cnm.prs.service.StatistiquesPreControleService;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3, étape 7) — <strong>le taux d'écartement par
 * règle</strong>, dans l'espace Administrateur.
 *
 * <p>Ressource séparée de {@code /api/pre-controle} et c'est délibéré : celle-là sert des
 * <strong>plans</strong> à ceux qui y ont droit, celle-ci ne sert que des <strong>compteurs</strong>.
 * L'Administrateur peut donc lire ce tableau de bord sans qu'on lui ouvre l'accès aux dossiers — son rôle
 * est technique. Le Président y a accès aussi : c'est lui qui, en dernier ressort, décide d'éteindre une
 * règle qui fatigue tout le monde.</p>
 *
 * <p>Ce que le tableau sert à faire, très concrètement : repérer la règle écartée dans 80 % des cas et
 * l'éteindre depuis l'écran des règles d'anomalie ({@code /api/regle-anomalies}), sans redéploiement.</p>
 */
@RestController
@RequestMapping("/api/pre-controle/statistiques")
@PreAuthorize("hasAnyRole('ADMINISTRATEUR','PRESIDENT')")
public class StatistiquesPreControleController {

    private final StatistiquesPreControleService service;

    public StatistiquesPreControleController(StatistiquesPreControleService service) {
        this.service = service;
    }

    /**
     * Les compteurs par règle, la plus écartée d'abord.
     *
     * @param exercice pour n'observer qu'un exercice ; omis, tous les exercices sont comptés
     */
    @GetMapping
    public StatistiquesReglesDto parRegle(@RequestParam(required = false) Integer exercice) {
        return service.parRegle(exercice);
    }
}
