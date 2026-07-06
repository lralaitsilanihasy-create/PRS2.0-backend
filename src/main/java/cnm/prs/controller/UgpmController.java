package cnm.prs.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.CreerUgpmRequest;
import cnm.prs.dto.UgpmDto;
import cnm.prs.service.UgpmService;
import jakarta.validation.Valid;

/**
 * UGPM (Administrateur) : création d'une UGPM + compte, et liste. Réservé au profil {@code ADMINISTRATEUR}.
 */
@RestController
@RequestMapping("/api/ugpms")
public class UgpmController {

    private final UgpmService service;

    public UgpmController(UgpmService service) {
        this.service = service;
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PostMapping
    public ResponseEntity<UgpmDto> creer(@Valid @RequestBody CreerUgpmRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(req));
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @GetMapping
    public List<UgpmDto> findAll() {
        return service.findAll();
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @GetMapping("/{id}")
    public UgpmDto findById(@PathVariable String id) {
        return service.findById(id);
    }
}
