package cnm.prs.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.CompteDesignableDto;
import cnm.prs.dto.ParametresInternesDto;
import cnm.prs.dto.ParametresInternesRequest;
import cnm.prs.dto.ResponsableProcedureDto;
import cnm.prs.dto.ResponsableRequest;
import cnm.prs.service.ParametresInternesService;
import jakarta.validation.Valid;

/**
 * ⚠️ V50 (2026-09-27, remise électronique, §B4 et §B5 ; ADR-0010) — sous la fiche ({@code /api/fiches-marche/{idDmc}}) :
 * les <strong>paramètres internes</strong> de la procédure, réservés au titulaire du rôle « Responsable de la procédure »
 * (403 pour tout autre, Administrateur et PRMP compris — la garde est dans le service, par identité), et le
 * <strong>rôle</strong> lui-même, désigné et retiré par l'Administrateur. Le journal global {@code t_audit_log} reçoit
 * la route de chaque écriture par l'intercepteur, sans valeurs (Q7).
 */
@RestController
@RequestMapping("/api/fiches-marche/{idDmc}")
public class ParametresInternesController {

    private final ParametresInternesService service;

    public ParametresInternesController(ParametresInternesService service) {
        this.service = service;
    }

    /** Les paramètres internes, leur état, leurs anomalies et leur journal (titulaire seul). */
    @GetMapping("/parametres-internes")
    public ParametresInternesDto lire(@PathVariable Long idDmc) {
        return service.lire(idDmc);
    }

    /**
     * Remplace les paramètres internes (titulaire seul) : 400 nominatifs ({@code membresCommission}, {@code quorum},
     * {@code dateCeremonie}), 409 {@code MEMBRE_COMMISSION}, 409 {@code FICHE_VALIDEE}.
     */
    @PutMapping("/parametres-internes")
    public ParametresInternesDto ecrire(@PathVariable Long idDmc, @RequestBody ParametresInternesRequest corps) {
        return service.ecrire(idDmc, corps);
    }

    /** Les comptes désignables comme membres détenteurs d'une part de clé (titulaire seul). */
    @GetMapping("/parametres-internes/candidats")
    public List<CompteDesignableDto> candidatsMembres(@PathVariable Long idDmc) {
        return service.candidatsMembres(idDmc);
    }

    /** Désigne le responsable de la procédure (Administrateur) → 201 sans relecture de la fiche. */
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PostMapping("/responsable")
    public ResponseEntity<ResponsableProcedureDto> designer(@PathVariable Long idDmc, @Valid @RequestBody ResponsableRequest corps) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.designer(idDmc, corps.im()));
    }

    /** Retire le responsable actif (Administrateur) → 204 ; 404 sans titulaire. */
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @DeleteMapping("/responsable")
    public ResponseEntity<Void> retirer(@PathVariable Long idDmc) {
        service.retirer(idDmc);
        return ResponseEntity.noContent().build();
    }

    /** Les comptes désignables comme responsable, hors membres de la commission de cette fiche (Administrateur). */
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @GetMapping("/responsable/candidats")
    public List<CompteDesignableDto> candidatsResponsable(@PathVariable Long idDmc) {
        return service.candidatsResponsable(idDmc);
    }
}
