package cnm.prs.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.validation.annotation.Validated;

import jakarta.validation.groups.Default;

import cnm.prs.dto.GroupesValidation;
import cnm.prs.dto.MarcheDto;
import cnm.prs.service.MarcheService;

/**
 * Contrôleur REST pour la ressource {@code marches} (table {@code t_marche}).
 */
@RestController
@RequestMapping("/api/marches")
public class MarcheController {

    private final MarcheService service;

    public MarcheController(MarcheService service) {
        this.service = service;
    }

    /**
     * ⚠️ Audit front (2026-08-16) — même liste, paginée ({@code ?page=&size=}) ; sans {@code page}, liste plate.
     *
     * <p>{@code ppm} (facultatif) restreint aux marchés d'un PPM. Le filtre est appliqué
     * <strong>avant</strong> la pagination : c'est ce qui permet à l'écran « Marchés » filtré par PPM
     * de ne plus télécharger la liste entière pour la découper lui-même.</p>
     */
    @GetMapping(params = "page")
    public org.springframework.data.domain.Page<MarcheDto> findAllPagine(
            @RequestParam(name = "ppm", required = false) Integer idPpm,
            org.springframework.data.domain.Pageable pageable) {
        return service.findAllPagine(idPpm, pageable);
    }

    /** Liste plate, avec le même filtre {@code ppm} facultatif (compatibilité : sans lui, inchangée). */
    @GetMapping
    public List<MarcheDto> findAll(@RequestParam(name = "ppm", required = false) Integer idPpm) {
        return service.findAll(idPpm);
    }

    @GetMapping("/{id}")
    public MarcheDto findById(@PathVariable Integer id) {
        return service.findById(id);
    }

    // Édition des lignes d'un brouillon : réservée à la PRMP (propriétaire) ; validé en service.
    @PreAuthorize("hasAnyRole('PRMP','UGPM')")
    @PostMapping
    public ResponseEntity<MarcheDto> create(@Validated({ Default.class, GroupesValidation.Identite.class }) @RequestBody MarcheDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PreAuthorize("hasAnyRole('PRMP','UGPM')")
    @PutMapping("/{id}")
    public MarcheDto update(@PathVariable Integer id, @Validated({ Default.class, GroupesValidation.Identite.class }) @RequestBody MarcheDto dto) {
        return service.update(id, dto);
    }

    /**
     * Édition restreinte (rectification) : PRMP propriétaire, uniquement si dossier EN_ATTENTE_DECISION_PRMP.
     *
     * <p>Validation du <strong>groupe {@code Default} seul</strong> : les champs d'identité figés
     * ({@code idDossier}/{@code idPpm}), que le front n'envoie pas en rectification, portent leur
     * {@code @NotNull} dans {@link GroupesValidation.Identite} et ne sont donc pas exigés — mais les
     * contraintes de <strong>contenu</strong> ({@code @Size}, montant) restent appliquées. Sans cela, un
     * corps trop long partait jusqu'à la base et revenait en 409 opaque au lieu d'un 400 nommant le champ.</p>
     */
    @PreAuthorize("hasAnyRole('PRMP','UGPM')")
    @PatchMapping("/{id}/rectifier")
    public MarcheDto rectifier(@PathVariable Integer id, @Validated @RequestBody MarcheDto dto) {
        return service.modifierEnAttenteRectification(id, dto);
    }

    @PreAuthorize("hasAnyRole('PRMP','UGPM')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
