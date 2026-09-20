package cnm.prs.controller;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
import cnm.prs.service.SyntheseDossierService;
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
    private final SyntheseDossierService synthese;

    public AssistantIaController(AssistantIaService service, SyntheseDossierService synthese) {
        this.service = service;
        this.synthese = synthese;
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
        return service.poser(requete.question(), demandeur(http));
    }

    /**
     * ⚠️ <strong>Synthèse d'un dossier</strong> (lot 2) — le premier geste de l'assistant qui touche une
     * donnée métier. Réponse en flux SSE : {@code faits} (ce que le serveur a lu, servi
     * <strong>avant</strong> toute génération), {@code texte} (morceaux de la rédaction), puis
     * {@code fin} ou {@code erreur}.
     *
     * <p>Aucune garde de profil ici, et c'est <strong>voulu</strong> : le périmètre est celui du dossier
     * lui-même. Le service appelle la liste blanche sous l'identité de l'appelant, et la lecture du
     * dossier est la porte — hors périmètre, elle répond <strong>403 avant l'ouverture du flux</strong>,
     * exactement comme si l'utilisateur avait ouvert le dossier à la main. Ajouter ici une liste de
     * rôles dupliquerait une règle qui vit déjà ailleurs, et les deux finiraient par diverger.</p>
     *
     * <p>404 si l'assistant n'est pas activé, ou si le dossier n'existe pas.</p>
     */
    @PostMapping(value = "/dossiers/{id}/synthese", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter synthetiser(@PathVariable Integer id, HttpServletRequest http) {
        return synthese.synthetiser(id, demandeur(http));
    }

    /** Qui demande — relevé dans le fil de la requête, avant que quoi que ce soit ne parte au pool. */
    private Demandeur demandeur(HttpServletRequest http) {
        return new Demandeur(CurrentUser.ref().orElse(null), CurrentUser.profil().orElse(null),
                http.getRemoteAddr());
    }
}
