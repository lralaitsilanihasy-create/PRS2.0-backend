package cnm.prs.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import cnm.prs.dto.EcartementRequest;
import cnm.prs.dto.ResumePreControleDto;
import cnm.prs.dto.SignalementDto;
import cnm.prs.service.SignalementPreControleService;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3, étape 3) — <strong>vérifier son plan avant de le
 * soumettre</strong>, et <strong>écarter un signalement non pertinent</strong> en le motivant.
 *
 * <p>Ouvert aux <strong>deux côtés du circuit</strong> : la PRMP (et l'agent de son UGPM) sur ses propres
 * plans, les contrôleurs sur ceux de leur commission. Le périmètre exact est tenu par
 * {@link SignalementPreControleService}, où vivent toutes les gardes — les annotations ci-dessous ne
 * ferment que la porte d'entrée par profil.</p>
 *
 * <p>Sont hors de cette ressource, volontairement : le <strong>Secrétaire</strong> et le
 * <strong>Chargé de publication</strong>, qui n'examinent pas ; et l'<strong>Administrateur</strong>, dont
 * le rôle est technique — son tableau de bord des taux d'écartement par règle (étape 7) lira des
 * <em>compteurs</em>, pas des plans.</p>
 *
 * <p><strong>Rien ici ne bloque</strong> : le pré-contrôle signale, il ne refuse aucune soumission et ne
 * corrige aucune saisie. Le mode de passation reste celui que la PRMP a écrit.</p>
 */
@RestController
@RequestMapping("/api/pre-controle")
@PreAuthorize("hasAnyRole('PRMP','UGPM','PRESIDENT','CHEF_COMMISSION','MEMBRE','VERIFICATEUR',"
        + "'ASSISTANT_CONTROLEUR')")
public class PreControleController {

    private final SignalementPreControleService service;

    public PreControleController(SignalementPreControleService service) {
        this.service = service;
    }

    /**
     * Relit les signalements d'un plan, sans relancer les règles — ce que l'écran affiche à l'ouverture.
     */
    @GetMapping("/ppm/{idPpm}")
    public ResumePreControleDto lire(@PathVariable Integer idPpm) {
        return service.lire(idPpm);
    }

    /**
     * « Vérifier mon PPM » : relance les règles sur le plan et rend le résultat.
     *
     * <p>Sur un bouton explicite, et non à chaque frappe (plan, 3.d). Idempotent : deux vérifications de
     * suite sur un plan inchangé ne créent aucun signalement de plus, et n'effacent aucun écartement.</p>
     */
    @PostMapping("/ppm/{idPpm}/verifier")
    public ResumePreControleDto verifier(@PathVariable Integer idPpm) {
        return service.verifier(idPpm);
    }

    /**
     * Écarte un signalement, avec un <strong>motif obligatoire</strong> et la confirmation que
     * l'avertissement de visibilité a été montré — « sans cela il n'y a pas de dissuasion, seulement un
     * piège » (plan, 3.f, condition 1).
     *
     * <p>L'Assistant contrôleur en est exclu : il prépare le travail de l'examinateur, il ne tranche pas.</p>
     */
    @PostMapping("/signalements/{id}/ecarter")
    @PreAuthorize("hasAnyRole('PRMP','UGPM','PRESIDENT','CHEF_COMMISSION','MEMBRE','VERIFICATEUR')")
    public SignalementDto ecarter(@PathVariable Integer id, @Valid @RequestBody EcartementRequest requete) {
        return service.ecarter(id, requete);
    }

    /**
     * Reprend son propre écartement, tant que le plan n'est pas soumis. Après la soumission, un écartement
     * est figé : c'est ce qui lui donne sa valeur devant l'autre côté du circuit.
     */
    @PostMapping("/signalements/{id}/reprendre")
    @PreAuthorize("hasAnyRole('PRMP','UGPM','PRESIDENT','CHEF_COMMISSION','MEMBRE','VERIFICATEUR')")
    public SignalementDto reprendre(@PathVariable Integer id) {
        return service.reprendre(id);
    }
}
