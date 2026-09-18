package cnm.prs.controller;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import cnm.prs.dto.EtatAssistantIaDto;
import cnm.prs.dto.QuestionIaRequest;
import cnm.prs.security.CurrentUser;
import cnm.prs.service.AssistantIaService;
import cnm.prs.service.AssistantIaService.Demandeur;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Assistant IA local — lot 1 : questions sur les règles du contrôle des marchés et de PRS
 * ({@code docs/plan-assistant-ia.md} ; ADR-0007).
 *
 * <p>Ouvert aux <strong>dix profils</strong>, PRMP comprise (décision du pilote, 2026-09-18) : la
 * réponse ne s'appuie que sur le corpus documentaire, identique pour tous — aucune donnée de dossier
 * n'est lue, il n'y a donc pas de périmètre à protéger. Lecture seule : la question est un POST
 * parce qu'elle porte un corps, pas parce qu'elle modifie quoi que ce soit.</p>
 */
@RestController
@RequestMapping("/api/assistant-ia")
@PreAuthorize("isAuthenticated()")
public class AssistantIaController {

    private final AssistantIaService service;

    public AssistantIaController(AssistantIaService service) {
        this.service = service;
    }

    /** L'assistant est-il activé, son service de calcul répond-il, sur quels documents s'appuie-t-il ? */
    @GetMapping("/etat")
    public EtatAssistantIaDto etat() {
        return service.etat();
    }

    /**
     * Pose une question. Réponse en flux SSE : {@code sources} (extraits numérotés), {@code texte}
     * (morceaux de la réponse, {@code {"t": "…"}}), puis {@code fin} ou {@code erreur}. 404 si
     * l'assistant n'est pas activé, 400 si la question est vide ou trop longue.
     */
    @PostMapping(value = "/questions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter poser(@Valid @RequestBody QuestionIaRequest requete, HttpServletRequest http) {
        Demandeur demandeur = new Demandeur(CurrentUser.ref().orElse(null), CurrentUser.profil().orElse(null),
                http.getRemoteAddr());
        return service.poser(requete.question(), demandeur);
    }
}
