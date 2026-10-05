package cnm.prs.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.CaoDto;
import cnm.prs.dto.DepositaireDto;
import cnm.prs.service.DepositaireService;
import jakarta.validation.Valid;

/**
 * ⚠️ 2026-10-05 (demande front « le dépositaire génère lui-même la part de secours », §B1) — l'<strong>espace du dépositaire</strong>
 * ({@code /api/depositaire/**}, profil {@code DEPOSITAIRE}, hors coquille interne) : l'activation (publique), ses procédures. Sa clé
 * vit sous {@code /api/fiches-marche/{idDmc}/ceremonie/cles/secours}, son apport sous {@code …/seance/parts?role=SECOURS}.
 */
@RestController
@RequestMapping("/api/depositaire")
public class EspaceDepositaireController {

    private final DepositaireService service;

    public EspaceDepositaireController(DepositaireService service) {
        this.service = service;
    }

    /** Public : 200 {@code { etat: 'ACTIF' }} ; 400 {@code CODE_INVALIDE} / {@code CODE_EXPIRE} ; 404 ; 429. */
    @PostMapping("/activation")
    public CaoDto.EtatCompte activer(@Valid @RequestBody CaoDto.Activation corps) {
        return service.activer(corps);
    }

    @GetMapping("/procedures")
    @PreAuthorize("hasRole('DEPOSITAIRE')")
    public List<DepositaireDto.Procedure> procedures() {
        return service.mesProcedures();
    }
}
