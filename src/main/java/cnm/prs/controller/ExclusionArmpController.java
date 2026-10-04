package cnm.prs.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.EntrepriseCandidatDto;
import cnm.prs.service.ExclusionArmpService;
import jakarta.validation.Valid;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 1b, §B5) — le <strong>répertoire des exclusions de
 * l'ARMP</strong>, tenu par l'Administrateur. Pas de suppression : on corrige, ou on avance la date de fin.
 */
@RestController
@RequestMapping("/api/exclusions-armp")
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class ExclusionArmpController {

    private final ExclusionArmpService service;

    public ExclusionArmpController(ExclusionArmpService service) {
        this.service = service;
    }

    @GetMapping
    public List<EntrepriseCandidatDto.Exclusion> lister() {
        return service.lister();
    }

    @PostMapping
    public ResponseEntity<EntrepriseCandidatDto.Exclusion> creer(@Valid @RequestBody EntrepriseCandidatDto.SaisieExclusion corps) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(corps));
    }

    @PutMapping("/{id}")
    public EntrepriseCandidatDto.Exclusion modifier(@PathVariable Integer id, @Valid @RequestBody EntrepriseCandidatDto.SaisieExclusion corps) {
        return service.modifier(id, corps);
    }
}
