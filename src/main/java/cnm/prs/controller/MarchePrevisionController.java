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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import cnm.prs.dto.MarchePrevisionDto;
import cnm.prs.service.MarchePrevisionService;

/**
 * Contrôleur REST pour la ressource {@code marche-previsions} (table {@code t_marche_prevision}).
 * Dates prévisionnelles des marchés (relation 1,N avec {@code t_marche}).
 *
 * <p>⚠️ LOT 3a (2026-08-26) — §1/§3.1, même politique que {@code LotController}. Particularité : la
 * garde d'écriture ne peut pas être posée dans {@code MarchePrevisionService.create(...)}, que
 * {@code SaisieService} appelle en interne — les écritures passent donc par les méthodes dédiées
 * {@code creerAvecGarde} / {@code modifierAvecGarde} / {@code supprimerAvecGarde}, appelées
 * <strong>uniquement</strong> depuis ce contrôleur (voir la javadoc du service).</p>
 */
@RestController
@RequestMapping("/api/marche-previsions")
public class MarchePrevisionController {

    private final MarchePrevisionService service;

    public MarchePrevisionController(MarchePrevisionService service) {
        this.service = service;
    }

    /** Liste toutes les dates prévisionnelles, ou celles d'un marché si {@code marche} est fourni. */
    @GetMapping
    public List<MarchePrevisionDto> findAll(@RequestParam(name = "marche", required = false) Integer idDetail) {
        return idDetail == null ? service.findAll() : service.findByMarche(idDetail);
    }

    @GetMapping("/{id}")
    public MarchePrevisionDto findById(@PathVariable Integer id) {
        return service.findById(id);
    }

    /** ⚠️ LOT 3a (2026-08-26) — §3.1 : écriture réservée à la PRMP/UGPM propriétaire d'un brouillon (ou Admin). */
    @PostMapping
    @PreAuthorize("hasAnyRole('PRMP','UGPM','ADMINISTRATEUR')")
    public ResponseEntity<MarchePrevisionDto> create(@Valid @RequestBody MarchePrevisionDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creerAvecGarde(dto));
    }

    /** ⚠️ LOT 3a (2026-08-26) — §3.1 : idem création. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PRMP','UGPM','ADMINISTRATEUR')")
    public MarchePrevisionDto update(@PathVariable Integer id, @Valid @RequestBody MarchePrevisionDto dto) {
        return service.modifierAvecGarde(id, dto);
    }

    /** ⚠️ LOT 3a (2026-08-26) — §3.1 : idem création. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('PRMP','UGPM','ADMINISTRATEUR')")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.supprimerAvecGarde(id);
        return ResponseEntity.noContent().build();
    }
}
