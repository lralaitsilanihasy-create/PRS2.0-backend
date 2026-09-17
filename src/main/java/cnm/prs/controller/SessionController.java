package cnm.prs.controller;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.SessionDto;
import cnm.prs.service.SessionService;

/**
 * ⚠️ Lot 6 (2026-09-17, demande front « espace d'administration » §B4) — <strong>journal des
 * connexions</strong>, en lecture seule.
 *
 * <p><strong>Accès : {@code ADMINISTRATEUR} et personne d'autre.</strong> La réponse réunit les
 * identifiants tentés, les adresses et les postes de tous les utilisateurs : anonyme → 401, tout autre
 * profil → 403.</p>
 *
 * <p><strong>Aucune écriture, et c'est le fond du besoin.</strong> Cette ressource remplace le CRUD
 * générique {@code /api/session-utilisateurs}, <strong>retiré</strong> : il laissait l'Administrateur
 * créer, modifier et supprimer des traces de connexion, c'est-à-dire forger une preuve ou effacer la
 * sienne. Un journal de preuve modifiable est pire qu'absent. Même raisonnement, et même conclusion,
 * que pour {@code /api/audit-logs} le 2026-08-27. Il n'y a donc ici ni {@code POST}, ni {@code PUT},
 * ni {@code DELETE} : ces méthodes répondent 405, et la table n'est écrite que par le login et le
 * logout ({@code JournalConnexionService}).</p>
 */
@RestController
@RequestMapping("/api/sessions")
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class SessionController {

    private final SessionService service;

    public SessionController(SessionService service) {
        this.service = service;
    }

    /**
     * {@code GET /api/sessions?acteur=&succes=&du=&au=&page=&size=} — page de connexions, de la plus
     * récente à la plus ancienne. Tous les critères sont facultatifs et se cumulent.
     *
     * @param acteur référence d'acteur ({@code IM_CONTROLEUR}, {@code ID_PRMP}, {@code ID_UGPM})
     *               <strong>ou</strong> login tenté : un échec sur un login inconnu ne porte pas de
     *               référence, filtrer sur la seule référence masquerait ce qu'on vient regarder
     * @param succes {@code true} = connexions acceptées, {@code false} = tentatives refusées ; absent =
     *               les deux
     * @param du     premier jour inclus ({@code AAAA-MM-JJ})
     * @param au     dernier jour inclus ({@code AAAA-MM-JJ}), la journée entière
     */
    @GetMapping
    public Page<SessionDto> rechercher(
            @RequestParam(required = false) String acteur,
            @RequestParam(required = false) Boolean succes,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            Pageable pageable) {
        return service.rechercher(acteur, succes, du, au, pageable);
    }
}
