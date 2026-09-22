package cnm.prs.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.BilanControlesDto;
import cnm.prs.dto.BlocValeursRequest;
import cnm.prs.dto.CadrageRequest;
import cnm.prs.dto.FicheMarcheDto;
import cnm.prs.dto.VersionFicheDto;
import cnm.prs.service.FicheMarcheService;
import jakarta.validation.Valid;

/**
 * ⚠️ Fiche marché d'un appel d'offres (demande front du 2026-09-22, §B3 à §B5) — ressource {@code fiches-marche},
 * adressée par le <strong>DMC</strong>. Lecture au périmètre du dossier ; écriture PRMP / UGPM propriétaires et
 * Administrateur ; validation PRMP seule (gardes dans {@link FicheMarcheService}).
 */
@RestController
@RequestMapping("/api/fiches-marche")
public class FicheMarcheController {

    private final FicheMarcheService service;

    public FicheMarcheController(FicheMarcheService service) {
        this.service = service;
    }

    /** La dernière version (virtuelle avant le premier enregistrement). */
    @GetMapping("/{idDmc}")
    public FicheMarcheDto lire(@PathVariable Long idDmc) {
        return service.lire(idDmc);
    }

    /** Les réponses du cadrage — remplace l'ensemble ; 400 nominatif par clé fautive. */
    @PutMapping("/{idDmc}/cadrage")
    public FicheMarcheDto cadrage(@PathVariable Long idDmc, @Valid @RequestBody CadrageRequest corps) {
        return service.ecrireCadrage(idDmc, corps.cadrage());
    }

    /** Les valeurs d'un bloc — remplace celles du bloc ; 400 nominatif par champ fautif. */
    @PutMapping("/{idDmc}/blocs/{bloc}")
    public FicheMarcheDto bloc(@PathVariable Long idDmc, @PathVariable String bloc,
            @Valid @RequestBody BlocValeursRequest corps) {
        return service.ecrireBloc(idDmc, bloc, corps.valeurs());
    }

    /** Recalcule le bilan des contrôles sans écrire. */
    @PostMapping("/{idDmc}/controler")
    public BilanControlesDto controler(@PathVariable Long idDmc) {
        return service.controler(idDmc);
    }

    /** Valide la version courante (PRMP seule) : 409 {@code CONTROLES_BLOQUANTS} si le bilan en porte. */
    @PostMapping("/{idDmc}/valider")
    public FicheMarcheDto valider(@PathVariable Long idDmc) {
        return service.valider(idDmc);
    }

    /** Ouvre la version suivante en brouillon, copie de la dernière validée. */
    @PostMapping("/{idDmc}/reviser")
    public FicheMarcheDto reviser(@PathVariable Long idDmc) {
        return service.reviser(idDmc);
    }

    /** Les versions validées, de la première à la dernière. */
    @GetMapping("/{idDmc}/versions")
    public List<VersionFicheDto> versions(@PathVariable Long idDmc) {
        return service.versions(idDmc);
    }

    /** Une version donnée, en entier. */
    @GetMapping("/{idDmc}/versions/{numero}")
    public FicheMarcheDto version(@PathVariable Long idDmc, @PathVariable Integer numero) {
        return service.lireVersion(idDmc, numero);
    }
}
