package cnm.prs.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.DiffDossierDto;
import cnm.prs.dto.DossierDto;
import cnm.prs.dto.MiseAJourRequest;
import cnm.prs.service.MiseAJourPpmService;
import cnm.prs.service.SaisiePpmImportService;

import jakarta.validation.Valid;

/**
 * ⚠️ <strong>Mise à jour d'un PPM (règle ajoutée 2026-08-05)</strong> — versionnement d'un dossier de
 * planification. Regroupé dans un contrôleur dédié plutôt qu'éclaté entre {@code SaisieController},
 * {@code DossierController} et {@code MarcheController} : les chemins restent ceux des ressources
 * concernées, mais la fonctionnalité se lit d'un seul tenant.
 *
 * <p>Tout est réservé à la <strong>PRMP propriétaire</strong> du dossier (vérifiée dans le service, pas
 * seulement par le rôle) : l'UGPM, qui saisit sous tutelle, n'ouvre pas de nouvelle version.</p>
 */
@RestController
@RequestMapping("/api")
public class MiseAJourPpmController {

    private final MiseAJourPpmService service;
    /** Parsing read-only du PPM PDF — la même façade que l'import de la saisie initiale. */
    private final SaisiePpmImportService importService;

    public MiseAJourPpmController(MiseAJourPpmService service, SaisiePpmImportService importService) {
        this.service = service;
        this.importService = importService;
    }

    /**
     * Ouvre la version n+1 d'un PPM en vigueur : crée un dossier {@code BROUILLON} copie conforme du
     * précédent, qu'il ne modifie pas. Le prédécesseur ne bascule en {@code REMPLACE} qu'à la soumission
     * de cette nouvelle version — une mise à jour abandonnée se supprime sans conséquence.
     *
     * @return le dossier créé (201)
     */
    @PreAuthorize("hasRole('PRMP')")
    @PostMapping("/saisies/ppm/{idDossier}/mise-a-jour")
    @ResponseStatus(HttpStatus.CREATED)
    public DossierDto creerMiseAJour(@PathVariable Integer idDossier,
            @Valid @RequestBody MiseAJourRequest requete) {
        return service.creerMiseAJour(idDossier, requete.motif());
    }

    /**
     * ⚠️ <strong>Mise à jour par IMPORT du PPM PDF</strong> (demande user 2026-08-05) — une mise à jour
     * arrive comme un document, comme la création : la PRMP importe le plan modifié plutôt que de le
     * ressaisir. Le PDF est parsé, ses lignes sont <strong>rapprochées</strong> de celles de la version
     * (identité conservée), les absentes passent en supprimées, les nouvelles sont créées.
     *
     * @return le diff recalculé — à vérifier avant de créer la mise à jour
     */
    @PreAuthorize("hasRole('PRMP')")
    @PostMapping(value = "/saisies/ppm/{idDossier}/mise-a-jour/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DiffDossierDto importerMiseAJour(@PathVariable Integer idDossier,
            @RequestPart("fichier") MultipartFile fichier) {
        return service.appliquerImport(idDossier, importService.importer(fichier));
    }

    /**
     * Comparaison d'une version avec son prédécesseur : récapitulatif chiffré + détail ligne à ligne
     * (« Aperçu du diff complet »). Recalculé tant que la version est un brouillon, relu depuis la trace
     * figée une fois soumise. 409 si le dossier n'est pas une mise à jour.
     */
    @PreAuthorize("hasRole('PRMP')")
    @GetMapping("/dossiers/{idDossier}/diff")
    public DiffDossierDto diff(@PathVariable Integer idDossier) {
        return service.diff(idDossier);
    }

    /**
     * Chaîne complète des versions du dossier, <strong>de la plus récente à l'initiale</strong>, quel que
     * soit le point d'entrée (v3 → v2 → v1).
     */
    @PreAuthorize("hasAnyRole('PRMP','PRESIDENT','CHEF_COMMISSION','MEMBRE','VERIFICATEUR','ADMINISTRATEUR')")
    @GetMapping("/dossiers/{idDossier}/versions")
    public List<DossierDto> versions(@PathVariable Integer idDossier) {
        return service.chaineVersions(idDossier);
    }

    /** Suppression LOGIQUE d'une ligne dans un brouillon de mise à jour (restaurable, jamais effacée). */
    @PreAuthorize("hasRole('PRMP')")
    @PatchMapping("/marches/{idDetail}/supprimer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimerLigne(@PathVariable Integer idDetail) {
        service.supprimerLigne(idDetail);
    }

    /** Remet en service une ligne supprimée (bouton « Restaurer »). */
    @PreAuthorize("hasRole('PRMP')")
    @PatchMapping("/marches/{idDetail}/restaurer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restaurerLigne(@PathVariable Integer idDetail) {
        service.restaurerLigne(idDetail);
    }
}
