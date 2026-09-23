package cnm.prs.controller;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.BilanControlesDto;
import cnm.prs.dto.BlocValeursRequest;
import cnm.prs.dto.CadrageRequest;
import cnm.prs.dto.DocumentFicheDto;
import cnm.prs.dto.DossierDto;
import cnm.prs.dto.FicheMarcheDto;
import cnm.prs.dto.FicheRattachableDto;
import cnm.prs.dto.VersionFicheDto;
import cnm.prs.entity.DocumentFicheMarche;
import cnm.prs.service.FicheMarcheDossierService;
import cnm.prs.service.FicheMarcheService;
import jakarta.validation.Valid;

/**
 * ⚠️ Fiche marché d'un appel d'offres (demande front du 2026-09-22, §B3 à §B5) — ressource {@code fiches-marche},
 * adressée par le <strong>DMC</strong>. Lecture au périmètre du dossier ; écriture PRMP / UGPM propriétaires et
 * Administrateur ; validation PRMP seule (gardes dans {@link FicheMarcheService}).
 *
 * <p>⚠️ Lot 1b (demande front du 2026-09-23) — la fiche <strong>produit le dossier</strong> soumis à la CNM
 * ({@code POST /{idDmc}/dossier}, PRMP seule) ; {@code GET /rattachables} sert les fiches validées sans dossier, pour
 * le rattachement de secours ({@code PUT /api/dossiers/{id}/fiche-marche}). Gardes dans {@link FicheMarcheDossierService}.</p>
 */
@RestController
@RequestMapping("/api/fiches-marche")
public class FicheMarcheController {

    private final FicheMarcheService service;
    private final FicheMarcheDossierService dossiers;

    public FicheMarcheController(FicheMarcheService service, FicheMarcheDossierService dossiers) {
        this.service = service;
        this.dossiers = dossiers;
    }

    /**
     * Les fiches rattachables (dernière version validée, DMC sans dossier) du périmètre de l'appelant ; avec
     * {@code idDossier}, liste vide si ce dossier ne lui est pas visible. Chemin littéral déclaré avant {@code /{idDmc}}.
     */
    @PreAuthorize("hasAnyRole('PRMP', 'UGPM')")
    @GetMapping("/rattachables")
    public List<FicheRattachableDto> rattachables(@RequestParam(required = false) Integer idDossier) {
        return dossiers.rattachables(idDossier);
    }

    /**
     * ⚠️ Lot 2a (2026-09-23) — le binaire d'un document généré, `attachment` sous son nom de fichier (le front ne
     * compose aucun nom). Périmètre de lecture de la fiche.
     */
    @GetMapping("/documents/{idDocument}/contenu")
    public ResponseEntity<byte[]> contenuDocument(@PathVariable Integer idDocument) {
        DocumentFicheMarche d = service.document(idDocument);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, Telechargements.disposition(d.getNomFichier()))
                .contentType(Telechargements.typeAutorise(d.getExtension()))
                .body(d.getContenu());
    }

    /**
     * ⚠️ Lot 2a (2026-09-23) — les documents générés de la version courante, ou de {@code version} : liste vide (200)
     * pour une version non validée ; 404 pour une version inconnue.
     */
    @GetMapping("/{idDmc}/documents")
    public List<DocumentFicheDto> documents(@PathVariable Long idDmc, @RequestParam(required = false) Integer version) {
        return service.documents(idDmc, version);
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

    /**
     * Produit le dossier {@code DMC} / {@code DAO} soumis à la CNM depuis la fiche validée (PRMP seule) : 201 et le
     * {@code DossierDto} créé, en brouillon ; 409 {@code FICHE_NON_VALIDEE}, {@code DOSSIER_EXISTANT} (avec
     * {@code idDossier}), {@code DMC_NON_DAO}.
     */
    @PreAuthorize("hasRole('PRMP')")
    @PostMapping("/{idDmc}/dossier")
    public ResponseEntity<DossierDto> creerDossier(@PathVariable Long idDmc) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dossiers.creerDossier(idDmc));
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
