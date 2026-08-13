package cnm.prs.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import cnm.prs.dto.VerificationPieceDepotDto;
import cnm.prs.service.VerificationPieceDepotService;

/**
 * ⚠️ Spec recevabilité au dépôt (2026-08-02) — ressource {@code verification-pieces-depot} :
 * contrôle de complétude des pièces jointes par le SECRÉTAIRE avant enregistrement de la réception.
 * Append-only (historisation) ; lecture authentifiée (historique d'un dossier).
 */
@RestController
@RequestMapping("/api/verification-pieces-depot")
public class VerificationPieceDepotController {

    private final VerificationPieceDepotService service;

    public VerificationPieceDepotController(VerificationPieceDepotService service) {
        this.service = service;
    }

    /** Historique des vérifications d'un dossier ({@code ?dossier=}, ASC — l'état courant = dernière par type). */
    @GetMapping
    public List<VerificationPieceDepotDto> historique(@RequestParam(name = "dossier") Integer dossier) {
        return service.historique(dossier);
    }

    /** Enregistre une décision (CONFORME / NON_CONFORME / MANQUANTE) — SECRÉTAIRE. */
    @PreAuthorize("hasRole('SECRETAIRE')")
    @PostMapping
    public ResponseEntity<VerificationPieceDepotDto> enregistrer(@Valid @RequestBody VerificationPieceDepotDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.enregistrer(dto));
    }
}
