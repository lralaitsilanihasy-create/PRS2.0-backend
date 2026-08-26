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

import jakarta.validation.Valid;

import cnm.prs.dto.ServiceBeneficiaireDto;
import cnm.prs.service.ServiceBeneficiaireService;

/**
 * Contrôleur REST pour la ressource {@code service-beneficiaires} (table {@code t_service_beneficiaire}).
 *
 * <p>⚠️ LOT 3a (2026-08-26) — §1/§3.1, même politique que {@code LotController} : lecture scopée dans
 * le service au périmètre du dossier parent (atteint via {@code ID_DETAIL → t_marche}), écriture
 * réservée à la PRMP/UGPM propriétaire d'un dossier en {@code BROUILLON}, ou à l'Administrateur.</p>
 */
@RestController
@RequestMapping("/api/service-beneficiaires")
public class ServiceBeneficiaireController {

    private final ServiceBeneficiaireService service;

    public ServiceBeneficiaireController(ServiceBeneficiaireService service) {
        this.service = service;
    }

    @GetMapping
    public List<ServiceBeneficiaireDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ServiceBeneficiaireDto findById(@PathVariable Integer id) {
        return service.findById(id);
    }

    /** ⚠️ LOT 3a (2026-08-26) — §3.1 : écriture réservée à la PRMP/UGPM propriétaire d'un brouillon (ou Admin). */
    @PostMapping
    @PreAuthorize("hasAnyRole('PRMP','UGPM','ADMINISTRATEUR')")
    public ResponseEntity<ServiceBeneficiaireDto> create(@Valid @RequestBody ServiceBeneficiaireDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    /** ⚠️ LOT 3a (2026-08-26) — §3.1 : idem création. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PRMP','UGPM','ADMINISTRATEUR')")
    public ServiceBeneficiaireDto update(@PathVariable Integer id, @Valid @RequestBody ServiceBeneficiaireDto dto) {
        return service.update(id, dto);
    }

    /** ⚠️ LOT 3a (2026-08-26) — §3.1 : idem création. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('PRMP','UGPM','ADMINISTRATEUR')")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
