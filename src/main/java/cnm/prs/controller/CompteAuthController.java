package cnm.prs.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import cnm.prs.dto.CompteAuthResumeDto;
import cnm.prs.dto.ReinitMotDePasseRequest;
import cnm.prs.service.CompteAuthService;

/**
 * Gestion des comptes d'authentification — réservée à l'Administrateur (§3.8) : suspendre et
 * rouvrir un compte <strong>déjà validé</strong>, réinitialiser un mot de passe.
 *
 * <p>⚠️ L'<strong>instruction des inscriptions</strong> (validation, refus motivé) n'est pas ici
 * mais sur {@code /api/inscriptions} ({@link cnm.prs.service.InscriptionService}) : depuis le
 * 2026-09-17, activer ou suspendre une inscription {@code EN_ATTENTE} ou {@code REFUSE} est refusé
 * (409).</p>
 */
@RestController
@RequestMapping("/api/comptes-auth")
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class CompteAuthController {

    private final CompteAuthService service;

    public CompteAuthController(CompteAuthService service) {
        this.service = service;
    }

    /** Liste des comptes en attente de validation (inactifs). */
    @GetMapping("/en-attente")
    public List<CompteAuthResumeDto> enAttente() {
        return service.enAttente();
    }

    /**
     * Rouvre un compte déjà validé (« réactiver »).
     *
     * <p>⚠️ Ce n'est <strong>pas</strong> la validation d'une inscription : celle-ci passe par
     * {@code POST /api/inscriptions/{login}/valider}. Sur une inscription {@code EN_ATTENTE} ou
     * {@code REFUSE} → <strong>409</strong> (garde du 2026-09-17,
     * {@link cnm.prs.service.CompteAuthService#activer}).</p>
     */
    @PostMapping("/{login}/activer")
    public CompteAuthResumeDto activer(@PathVariable String login) {
        return service.activer(login);
    }

    /**
     * Suspend un compte déjà validé.
     *
     * <p>⚠️ Sur une inscription {@code EN_ATTENTE} ou {@code REFUSE} → <strong>409</strong> : il n'y
     * a pas de compte ouvert à fermer ; bloquer une inscription se dit
     * {@code POST /api/inscriptions/{login}/refuser}, avec motif.</p>
     */
    @PostMapping("/{login}/desactiver")
    public CompteAuthResumeDto desactiver(@PathVariable String login) {
        return service.desactiver(login);
    }

    /** Réinitialise le mot de passe d'un compte (utilisateur ayant oublié le sien). */
    @PostMapping("/{login}/reinitialiser-mot-de-passe")
    public CompteAuthResumeDto reinitialiserMotDePasse(@PathVariable String login,
            @Valid @RequestBody ReinitMotDePasseRequest request) {
        return service.reinitialiserMotDePasse(login, request.nouveauMotDePasse());
    }
}
