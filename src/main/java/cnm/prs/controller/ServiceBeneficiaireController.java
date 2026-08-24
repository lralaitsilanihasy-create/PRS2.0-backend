package cnm.prs.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import jakarta.validation.Valid;

import cnm.prs.dto.ServiceBeneficiaireDto;
import cnm.prs.service.ServiceBeneficiaireService;

/**
 * Contrôleur REST pour la ressource {@code service-beneficiaires} (table {@code t_service_beneficiaire}).
 *
 * <p>Un service bénéficiaire est une <strong>ressource fille de la ligne de marché</strong> : les
 * lectures sont scopées au périmètre du marché parent (dans
 * {@link cnm.prs.service.ServiceBeneficiaireService}), les écritures portent les mêmes rôles que celles
 * de {@code /api/marches} — {@code PRMP} / {@code UGPM}, la ventilation budgétaire d'un PPM étant le
 * fait de son propriétaire, jamais du circuit interne CNM.</p>
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

    // Écriture : mêmes rôles que sur la ligne de marché parente (cf. MarcheController / LotController).
    // Le périmètre (marché visé) est contrôlé en service — le rôle seul n'empêcherait pas une PRMP
    // d'écrire la ventilation budgétaire d'une autre entité.
    @PreAuthorize("hasAnyRole('PRMP','UGPM')")
    @PostMapping
    public ResponseEntity<ServiceBeneficiaireDto> create(@Valid @RequestBody ServiceBeneficiaireDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PreAuthorize("hasAnyRole('PRMP','UGPM')")
    @PutMapping("/{id}")
    public ServiceBeneficiaireDto update(@PathVariable Integer id, @Valid @RequestBody ServiceBeneficiaireDto dto) {
        return service.update(id, dto);
    }

    @PreAuthorize("hasAnyRole('PRMP','UGPM')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
