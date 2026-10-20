package cnm.prs.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.ModelePvDto;
import cnm.prs.entity.VersionModelePv;
import cnm.prs.service.ModelePvService;
import jakarta.validation.Valid;

/**
 * ⚠️ 2026-10-20 (demande front « generateur-modeles-pv », §B4, lot G1) — les modèles de PV, réservés à l'<strong>Administrateur</strong>
 * (403 sinon). Codes d'erreur : 400 {@code NATURE_INCONNUE}, {@code CODE_INVALIDE}, {@code FICHIER_INVALIDE}, {@code CONDITION_INVALIDE},
 * {@code CHAMP_INVALIDE}, {@code SOUS_TYPE_INCONNU}, {@code MOTIF_REQUIS}, {@code AVIS_INCONNU} ; 409 {@code CODE_DEJA_PRIS},
 * {@code MODELE_ARCHIVE}, {@code VERSION_FIGEE}, {@code RAPPORT_BLOQUANT}, {@code SOUS_TYPE_DEJA_SERVI}, {@code MODELE_EN_SERVICE},
 * {@code CONVERSION_INDISPONIBLE}, {@code APERCU_IMPOSSIBLE} ; 404.
 */
@RestController
@RequestMapping("/api/modeles-pv")
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class ModelePvController {

    private static final MediaType DOCX = MediaType
            .parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final ModelePvService service;

    public ModelePvController(ModelePvService service) {
        this.service = service;
    }

    @GetMapping
    public List<ModelePvDto.Resume> lister() {
        return service.lister();
    }

    /** Le catalogue des variables, filtré par la famille du sous-type (§B3). */
    @GetMapping("/variables")
    public ModelePvDto.Catalogue variables(@RequestParam(required = false) String sousType) {
        return service.catalogue(sousType);
    }

    /** Les modèles intégrés au JAR, choisis par le code (« modeles-pv-integres », §B1). */
    @GetMapping("/integres")
    public List<ModelePvDto.Integre> integres() {
        return service.integres();
    }

    /** Par sous-type et par nature, ce qui produit le document : INTEGRE, GERE ou AUCUN (« modeles-pv-integres », §B2). */
    @GetMapping("/couverture")
    public List<ModelePvDto.Couverture> couverture() {
        return service.couverture();
    }

    @PostMapping
    public ModelePvDto.Fiche creer(@Valid @RequestBody ModelePvDto.CreationRequest corps) {
        return service.creer(corps);
    }

    @GetMapping("/{id}")
    public ModelePvDto.Fiche lire(@PathVariable Long id) {
        return service.lire(id);
    }

    @PostMapping("/{id}/dupliquer")
    public ModelePvDto.Fiche dupliquer(@PathVariable Long id, @Valid @RequestBody(required = false) ModelePvDto.DuplicationRequest corps) {
        return service.dupliquer(id, corps);
    }

    /** Multipart, partie {@code fichier} : le {@code .docx}. Rend la version en brouillon et son rapport de contrôle. */
    @PostMapping("/{id}/versions")
    public ModelePvDto.Version deposer(@PathVariable Long id, @RequestParam("fichier") MultipartFile fichier) {
        return service.deposer(id, fichier);
    }

    @GetMapping("/{id}/versions/{numero}")
    public ModelePvDto.Version version(@PathVariable Long id, @PathVariable Integer numero) {
        return service.version(id, numero);
    }

    /** Remplace les conditions de la version (brouillon) ; le rapport est recalculé. */
    @PutMapping("/{id}/versions/{numero}/conditions")
    public ModelePvDto.Version conditions(@PathVariable Long id, @PathVariable Integer numero,
            @Valid @RequestBody List<ModelePvDto.Condition> corps) {
        return service.declarerConditions(id, numero, corps);
    }

    /** Remplace les champs saisis de la version (brouillon) ; le rapport est recalculé. */
    @PutMapping("/{id}/versions/{numero}/champs")
    public ModelePvDto.Version champs(@PathVariable Long id, @PathVariable Integer numero, @Valid @RequestBody List<ModelePvDto.Champ> corps) {
        return service.declarerChamps(id, numero, corps);
    }

    /**
     * L'aperçu sur un dossier réel, sans rien enregistrer : {@code format=pdf} (défaut) ou {@code docx} ; {@code avis} force l'avis
     * (FAV, FAVR, DEF, NSP) ; le corps facultatif porte les champs saisis.
     */
    @PostMapping("/{id}/versions/{numero}/apercu")
    public ResponseEntity<byte[]> apercu(@PathVariable Long id, @PathVariable Integer numero, @RequestParam Integer dossier,
            @RequestParam(required = false) String avis, @RequestParam(defaultValue = "pdf") String format,
            @RequestBody(required = false) ModelePvDto.ApercuRequest corps) {
        boolean pdf = !"docx".equalsIgnoreCase(format);
        byte[] contenu = service.apercu(id, numero, dossier, avis, pdf, corps == null ? null : corps.saisis());
        return ResponseEntity.ok().contentType(pdf ? MediaType.APPLICATION_PDF : DOCX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename("apercu-" + id + "-v" + numero + "-dossier-" + dossier + (pdf ? ".pdf" : ".docx"), StandardCharsets.UTF_8)
                        .build().toString())
                .body(contenu);
    }

    @PostMapping("/{id}/versions/{numero}/mettre-en-vigueur")
    public ModelePvDto.Fiche mettreEnVigueur(@PathVariable Long id, @PathVariable Integer numero,
            @Valid @RequestBody(required = false) ModelePvDto.MotifRequest corps) {
        return service.mettreEnVigueur(id, numero, corps == null ? null : corps.motif());
    }

    @PutMapping("/{id}/rattachements")
    public ModelePvDto.Fiche rattacher(@PathVariable Long id, @RequestBody ModelePvDto.RattachementsRequest corps) {
        return service.rattacher(id, corps == null ? null : corps.sousTypes());
    }

    @PostMapping("/{id}/archiver")
    public ModelePvDto.Fiche archiver(@PathVariable Long id, @Valid @RequestBody(required = false) ModelePvDto.MotifRequest corps) {
        return service.archiver(id, corps == null ? null : corps.motif());
    }

    @GetMapping("/{id}/versions/{numero}/fichier")
    public ResponseEntity<byte[]> fichier(@PathVariable Long id, @PathVariable Integer numero) {
        VersionModelePv v = service.fichier(id, numero);
        return ResponseEntity.ok().contentType(DOCX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(v.getNomFichier(), StandardCharsets.UTF_8).build().toString())
                .body(v.getContenu());
    }
}
