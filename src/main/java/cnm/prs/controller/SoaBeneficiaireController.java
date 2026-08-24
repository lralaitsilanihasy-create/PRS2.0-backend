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

import cnm.prs.dto.SoaBeneficiaireDto;
import cnm.prs.service.SoaBeneficiaireService;

/**
 * Contrôleur REST pour la ressource {@code soa-beneficiaires} (table {@code tr_soa_beneficiaire}).
 *
 * <p><strong>Référentiel</strong> (code SOA + libellé) : aucune donnée de périmètre n'y transite, la
 * lecture reste donc ouverte à tout utilisateur authentifié, comme les autres référentiels. Les
 * écritures suivent la règle des référentiels — {@code ADMINISTRATEUR} — avec la même exception que
 * {@code /api/entite-contracts} et {@code /api/ministeres} : la <strong>création</strong> est ouverte à
 * la {@code PRMP} / {@code UGPM}, qui enregistre à l'import d'un PPM les codes SOA que sa ventilation
 * cite et que le référentiel ignore encore. {@code PUT} et {@code DELETE} restent Administrateur : une
 * PRMP n'a pas à renommer ni à retirer un code que d'autres entités utilisent.</p>
 */
@RestController
@RequestMapping("/api/soa-beneficiaires")
public class SoaBeneficiaireController {

    private final SoaBeneficiaireService service;

    public SoaBeneficiaireController(SoaBeneficiaireService service) {
        this.service = service;
    }

    @GetMapping
    public List<SoaBeneficiaireDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public SoaBeneficiaireDto findById(@PathVariable String id) {
        return service.findById(id);
    }

    // ⚠️ Création ouverte à la PRMP/UGPM EN PLUS de l'Admin — même motif que POST /api/entite-contracts
    // et POST /api/ministeres (cf. SecurityConfig) : à l'import d'un PPM, la ventilation budgétaire cite
    // des codes SOA absents du référentiel, que la PRMP enregistre depuis l'écran de soumission avant de
    // pouvoir soumettre. La ressource ne porte que SOA_CODE + LIBELLE : aucune donnée d'une autre entité
    // n'y transite, l'ouverture reste sans effet de bord sur les périmètres.
    @PreAuthorize("hasAnyRole('PRMP','UGPM','ADMINISTRATEUR')")
    @PostMapping
    public ResponseEntity<SoaBeneficiaireDto> create(@Valid @RequestBody SoaBeneficiaireDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PutMapping("/{id}")
    public SoaBeneficiaireDto update(@PathVariable String id, @Valid @RequestBody SoaBeneficiaireDto dto) {
        return service.update(id, dto);
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
