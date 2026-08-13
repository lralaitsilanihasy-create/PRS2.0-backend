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

import cnm.prs.dto.TransmissionSigmpDto;
import cnm.prs.service.TransmissionSigmpService;

/**
 * ⚠️ Spec navette (2026-08-01) — ressource {@code sigmp-transmissions} : transmissions du sens de la
 * décision de la Commission vers SIGMP (enregistrées côté PRS 2.0 en attendant l'API SIGMP réelle).
 * POST réservé au VÉRIFICATEUR (localité contrôlée dans le service) ; lecture authentifiée.
 */
@RestController
@RequestMapping("/api/sigmp-transmissions")
public class TransmissionSigmpController {

    private final TransmissionSigmpService service;

    public TransmissionSigmpController(TransmissionSigmpService service) {
        this.service = service;
    }

    @GetMapping
    public List<TransmissionSigmpDto> findAll(@RequestParam(name = "dossier", required = false) Integer dossier) {
        return dossier != null ? service.findByDossier(dossier) : service.findAll();
    }

    /** Transmet le sens de la décision du dossier (corps : {@code { idDossier }}) — VÉRIFICATEUR. */
    @PreAuthorize("hasRole('VERIFICATEUR')")
    @PostMapping
    public ResponseEntity<TransmissionSigmpDto> transmettre(@Valid @RequestBody TransmissionSigmpDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.transmettre(dto.getIdDossier()));
    }
}
