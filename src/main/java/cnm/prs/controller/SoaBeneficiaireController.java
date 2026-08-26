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

import cnm.prs.dto.SoaBeneficiaireDto;
import cnm.prs.service.SoaBeneficiaireService;

/**
 * Contrôleur REST pour la ressource {@code soa-beneficiaires} (table {@code tr_soa_beneficiaire}).
 *
 * <p>⚠️ LOT 3a (2026-08-26) — <strong>écart assumé</strong> par rapport à la politique des autres
 * enfants de saisie PPM. {@code tr_soa_beneficiaire} n'est pas un enfant de dossier : c'est un
 * <strong>référentiel</strong> ({@code SOA_CODE}, {@code LIBELLE}) sans aucun rattachement à un
 * dossier — il n'y a donc pas de périmètre à appliquer. La politique retenue est celle des autres
 * référentiels (§3.8 Module 03), avec la même exception que
 * {@code POST /api/entite-contracts} et {@code POST /api/ministeres} :</p>
 * <ul>
 *   <li><strong>Lecture</strong> : tout authentifié (listes déroulantes de la saisie) ;</li>
 *   <li><strong>Création</strong> : PRMP / UGPM et Administrateur — à l'import d'un PPM, la PRMP
 *       enregistre les codes SOA absents du référentiel (flux réel de
 *       {@code features/prmp/soumettre-dossier}) ; le fermer à l'Administrateur casserait cet import ;</li>
 *   <li><strong>Modification / suppression</strong> : Administrateur seul — renommer ou retirer un
 *       code du référentiel touche toutes les PRMP, ce n'est pas un acte de saisie.</li>
 * </ul>
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

    /** ⚠️ LOT 3a (2026-08-26) — création ouverte à la PRMP/UGPM (import PPM) et à l'Admin (voir en-tête). */
    @PostMapping
    @PreAuthorize("hasAnyRole('PRMP','UGPM','ADMINISTRATEUR')")
    public ResponseEntity<SoaBeneficiaireDto> create(@Valid @RequestBody SoaBeneficiaireDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    /** ⚠️ LOT 3a (2026-08-26) — modification du référentiel : Administrateur seul. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public SoaBeneficiaireDto update(@PathVariable String id, @Valid @RequestBody SoaBeneficiaireDto dto) {
        return service.update(id, dto);
    }

    /** ⚠️ LOT 3a (2026-08-26) — suppression du référentiel : Administrateur seul. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
