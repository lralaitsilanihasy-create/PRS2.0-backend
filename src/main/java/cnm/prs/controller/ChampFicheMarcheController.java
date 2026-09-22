package cnm.prs.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.ChampFicheMarcheDto;
import cnm.prs.dto.ReferentielFicheMarcheDto;
import cnm.prs.service.ChampFicheMarcheService;
import jakarta.validation.Valid;

/**
 * ⚠️ Fiche marché d'un appel d'offres (demande front du 2026-09-22, §B1) — référentiel {@code champs-fiche-marche} :
 * la structure (blocs, rubriques) et les champs d'où le front dessine l'écran. Lecture ouverte à tout authentifié,
 * POST/PUT Administrateur ({@code SecurityConfig.REFERENTIELS}) ; pas de DELETE, un champ se désactive.
 */
@RestController
@RequestMapping("/api/champs-fiche-marche")
public class ChampFicheMarcheController {

    private final ChampFicheMarcheService service;

    public ChampFicheMarcheController(ChampFicheMarcheService service) {
        this.service = service;
    }

    /** Structure et champs actifs d'un type de marché ; sans {@code typeMarche}, tout (vue d'administration). */
    @GetMapping
    public ReferentielFicheMarcheDto referentiel(@RequestParam(required = false) String typeMarche) {
        return service.referentiel(typeMarche);
    }

    @PostMapping
    public ResponseEntity<ChampFicheMarcheDto> creer(@Valid @RequestBody ChampFicheMarcheDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(dto));
    }

    @PutMapping("/{code}")
    public ChampFicheMarcheDto modifier(@PathVariable String code, @Valid @RequestBody ChampFicheMarcheDto dto) {
        return service.modifier(code, dto);
    }
}
