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
import cnm.prs.dto.VersionArchiveeDetailDto;
import cnm.prs.dto.VersionArchiveeDto;
import cnm.prs.service.MiseAJourPpmService;
import cnm.prs.service.RectificationDiffService;
import cnm.prs.service.SaisiePpmImportService;
import cnm.prs.service.VersionDossierService;

import jakarta.validation.Valid;

/**
 * ⚠️ <strong>Mise à jour d'un PPM (règle ajoutée 2026-08-05)</strong> — versionnement d'un dossier de
 * planification. Regroupé dans un contrôleur dédié plutôt qu'éclaté entre {@code SaisieController},
 * {@code DossierController} et {@code MarcheController} : les chemins restent ceux des ressources
 * concernées, mais la fonctionnalité se lit d'un seul tenant.
 *
 * <p>L'ouverture d'une version est réservée à la <strong>PRMP propriétaire</strong> du dossier (vérifiée
 * dans le service, pas seulement par le rôle) : l'UGPM, qui saisit sous tutelle, n'ouvre pas de nouvelle
 * version. Les lectures (diffs, chaîne des versions, versions archivées) sont ouvertes au circuit.</p>
 *
 * <p>⚠️ Deux notions de « version » coexistent ici, volontairement distinguées par le chemin :
 * {@code /versions} = la <em>chaîne des mises à jour</em> (chaque version est un dossier à part entière) ;
 * {@code /versions-archivees} = l'<em>historique des rectifications</em> d'un dossier (versions figées
 * du même dossier, 2026-09-06).</p>
 */
@RestController
@RequestMapping("/api")
public class MiseAJourPpmController {

    private static final String LECTURE_CIRCUIT = "hasAnyRole('PRMP','PRESIDENT','CHEF_COMMISSION','SECRETAIRE',"
            + "'MEMBRE','VERIFICATEUR','ASSISTANT_CONTROLEUR','ADMINISTRATEUR')";

    private final MiseAJourPpmService service;
    /** Parsing read-only du PPM PDF — la même façade que l'import de la saisie initiale. */
    private final SaisiePpmImportService importService;
    /** ⚠️ Visibilité des rectifications (2026-08-15) — diff du dernier cycle de rectification. */
    private final RectificationDiffService rectificationDiffService;
    /** ⚠️ Versions archivées (2026-09-06) — historique des rectifications d'un dossier. */
    private final VersionDossierService versionDossierService;

    public MiseAJourPpmController(MiseAJourPpmService service, SaisiePpmImportService importService,
            RectificationDiffService rectificationDiffService, VersionDossierService versionDossierService) {
        this.service = service;
        this.importService = importService;
        this.rectificationDiffService = rectificationDiffService;
        this.versionDossierService = versionDossierService;
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
     *
     * <p>⚠️ Lecture ÉLARGIE (2026-08-15, demande en attente depuis le 05/08) : plus réservé à la PRMP —
     * ouvert aux profils du circuit qui consultent le dossier (le surlignage MODIFIEE du tableau partagé
     * était privé de donnée par le 403). Contrôle serveur : PRMP propriétaire, sinon périmètre de
     * localité habituel.</p>
     */
    @PreAuthorize(LECTURE_CIRCUIT)
    @GetMapping("/dossiers/{idDossier}/diff")
    public DiffDossierDto diff(@PathVariable Integer idDossier) {
        return service.diff(idDossier);
    }

    /**
     * ⚠️ Règle ajoutée (2026-08-15, visibilité des rectifications) — diff du <strong>dernier cycle de
     * rectification</strong> (dernière version archivée vs lignes courantes), même DTO que le diff des
     * versions pour que le front réutilise son tableau tel quel. 409 si aucune rectification enregistrée.
     * Endpoint DÉDIÉ (décision backend) : {@code /diff} garde son contrat « mise à jour » (409 si pas de
     * version précédente) — un dossier peut être à la fois une version ET porter une rectification, les
     * deux diffs coexistent et le front choisit selon le contexte. Contrat <strong>inchangé</strong> par
     * l'archivage des versions (2026-09-06).
     */
    @PreAuthorize(LECTURE_CIRCUIT)
    @GetMapping("/dossiers/{idDossier}/diff-rectification")
    public DiffDossierDto diffRectification(@PathVariable Integer idDossier) {
        return rectificationDiffService.diffRectification(idDossier);
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

    /**
     * ⚠️ <strong>Versions archivées (demande pilote du 2026-09-06)</strong> — historique des rectifications
     * du dossier : une entrée par version <em>remplacée</em> (numéro, date, auteur, origine, cycle, nombre
     * de lignes), de la plus ancienne à la plus récente. <strong>Liste vide</strong> pour un dossier jamais
     * rectifié (200, pas 409 — contrairement au diff, il n'y a rien d'anormal à n'avoir pas d'historique).
     * La version <em>courante</em> n'y figure pas : c'est le dossier lui-même. Même périmètre de lecture
     * que le diff de rectification. <strong>Ressource en lecture seule</strong> : toute autre méthode
     * répond 405.
     */
    @PreAuthorize(LECTURE_CIRCUIT)
    @GetMapping("/dossiers/{idDossier}/versions-archivees")
    public List<VersionArchiveeDto> versionsArchivees(@PathVariable Integer idDossier) {
        return versionDossierService.lister(idDossier);
    }

    /**
     * ⚠️ Versions archivées (2026-09-06) — contenu complet d'une version archivée : en-tête + lignes de
     * marché avec bénéficiaires, lots et dates prévisionnelles, telles qu'elles étaient. 404 si le numéro
     * n'existe pas pour ce dossier. Lecture seule (405 sur toute autre méthode).
     */
    @PreAuthorize(LECTURE_CIRCUIT)
    @GetMapping("/dossiers/{idDossier}/versions-archivees/{numero}")
    public VersionArchiveeDetailDto versionArchivee(@PathVariable Integer idDossier, @PathVariable Integer numero) {
        return versionDossierService.detail(idDossier, numero);
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
