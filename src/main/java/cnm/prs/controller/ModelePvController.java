package cnm.prs.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.ModelePvDto;
import cnm.prs.entity.VersionModelePv;
import cnm.prs.entity.ImageModelePv;
import cnm.prs.service.ImagesModelePvService;
import cnm.prs.service.ModelePvService;
import cnm.prs.service.ModelesIntegresService;
import jakarta.validation.Valid;

/**
 * ⚠️ 2026-10-20 (demande front « generateur-modeles-pv », §B4, lot G1) — les modèles de PV, réservés à l'<strong>Administrateur</strong>
 * (403 sinon). Codes d'erreur : 400 {@code NATURE_INCONNUE}, {@code CODE_INVALIDE}, {@code FICHIER_INVALIDE}, {@code CONDITION_INVALIDE},
 * {@code CHAMP_INVALIDE}, {@code SOUS_TYPE_INCONNU}, {@code MOTIF_REQUIS}, {@code AVIS_INCONNU}, {@code SANS_SOUS_TYPE},
 * {@code NATURE_SANS_DEPART}, {@code PORTEE_INCONNUE} ; 409 {@code FAMILLES_MULTIPLES}, {@code FAMILLE_SANS_DEPART}, {@code CODE_DEJA_PRIS},
 * {@code MODELE_ARCHIVE}, {@code VERSION_FIGEE}, {@code RAPPORT_BLOQUANT}, {@code SOUS_TYPE_DEJA_SERVI}, {@code MODELE_EN_SERVICE},
 * {@code CONVERSION_INDISPONIBLE}, {@code APERCU_IMPOSSIBLE} ; 404.
 *
 * <p>⚠️ 2026-10-21 (demande front « modele-pv-page », lots P1-P2) — une version est une page : {@code POST /{id}/versions} reçoit
 * {@code { document, motif? }} en JSON, {@code PUT /{id}/versions/{n}/document} modifie un brouillon, l'aperçu d'une page est un PDF.
 * Codes ajoutés : 400 {@code DOCUMENT_INVALIDE} ({@code details} chemin → raison), {@code FORMAT_INDISPONIBLE}, {@code IMAGE_INVALIDE} ;
 * 409 {@code SUPPORT_ABANDONNE} (dépôt d'un Word, mise en vigueur ou modification d'une version Word), {@code VERSION_SANS_FICHIER}.
 * Images : {@code POST /images}, {@code GET /images/{idImage}}, {@code GET /images/embleme}. Schéma : {@code GET /schema}.</p>
 */
@RestController
@RequestMapping("/api/modeles-pv")
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class ModelePvController {

    private static final MediaType DOCX = MediaType
            .parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final ModelePvService service;
    private final ModelesIntegresService integres;
    private final ImagesModelePvService images;

    public ModelePvController(ModelePvService service, ModelesIntegresService integres, ImagesModelePvService images) {
        this.service = service;
        this.integres = integres;
        this.images = images;
    }

    /** ⚠️ 2026-10-21 (« modele-pv-page », §B1) — le schéma du document : nœuds, attributs, listes fermées, limites. */
    @GetMapping("/schema")
    public java.util.Map<String, Object> schema() {
        return service.schema();
    }

    /** ⚠️ 2026-10-21 (« modele-pv-page », §B3) — multipart, partie {@code fichier} : PNG ou JPEG, 2 Mo au plus. */
    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ModelePvDto.ImageDeposee deposerImage(@RequestParam("fichier") MultipartFile fichier) {
        return images.deposer(fichier);
    }

    /** L'emblème des PV officiels, fourni par l'application (créé au premier appel). */
    @GetMapping("/images/embleme")
    public ModelePvDto.ImageDeposee embleme() {
        return images.embleme();
    }

    /** L'image, servie avec son type contrôlé, jamais celui déclaré au dépôt. */
    @GetMapping("/images/{idImage:\\d+}")
    public ResponseEntity<byte[]> image(@PathVariable Long idImage) {
        ImageModelePv i = images.lire(idImage);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(i.getType()))
                .header("X-Content-Type-Options", "nosniff").body(i.getContenu());
    }

    @GetMapping
    public List<ModelePvDto.Resume> lister() {
        return service.lister();
    }

    /** Le catalogue des variables, filtré par la famille du sous-type (§B3) et par la nature (« modele-lettre-renvoi », B1). */
    @GetMapping("/variables")
    public ModelePvDto.Catalogue variables(@RequestParam(required = false) String sousType,
            @RequestParam(required = false) String nature, @RequestParam(required = false) String sigle) {
        return service.catalogue(sousType, nature, sigle);
    }

    /** ⚠️ 2026-10-22 (« modeles-dao », D0) — les 15 documents intégrés du DAO, leurs couvertures et leur état de service. */
    @GetMapping("/integres/dao")
    public List<ModelePvDto.IntegreDao> integresDao() {
        return service.integresDao();
    }

    /** ⚠️ 2026-10-22 (D0) — le document intégré d'un sigle en rendu brut (jetons visibles), {@code format=pdf} (défaut) ou {@code docx}. */
    @GetMapping("/integres/dao/{sigle}/fichier")
    public ResponseEntity<byte[]> integreDaoFichier(@PathVariable String sigle, @RequestParam(defaultValue = "pdf") String format) {
        boolean pdf = !"docx".equalsIgnoreCase(format);
        return ResponseEntity.ok().contentType(pdf ? MediaType.APPLICATION_PDF : DOCX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(sigle + "-integre" + (pdf ? ".pdf" : ".docx"), StandardCharsets.UTF_8).build().toString())
                .body(service.integreDaoBrut(sigle, pdf));
    }

    /** ⚠️ 2026-10-22 (D0) — le document intégré rendu sur la fiche validée d'une procédure, pour un lot, sans rien enregistrer. */
    @PostMapping("/integres/dao/{sigle}/apercu")
    public ResponseEntity<byte[]> integreDaoApercu(@PathVariable String sigle, @RequestParam Long procedure,
            @RequestParam(required = false) Integer lot, @RequestParam(defaultValue = "pdf") String format) {
        boolean pdf = !"docx".equalsIgnoreCase(format);
        return ResponseEntity.ok().contentType(pdf ? MediaType.APPLICATION_PDF : DOCX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename("apercu-" + sigle + "-procedure-" + procedure + (lot == null ? "" : "-lot-" + lot) + (pdf ? ".pdf" : ".docx"),
                                StandardCharsets.UTF_8).build().toString())
                .body(service.integreDaoApercu(sigle, procedure, lot, pdf));
    }

    /** ⚠️ 2026-10-22 (« modeles-dao », D-ARMP) — les écarts d'une version d'un document du DAO avec son document type. */
    @GetMapping("/{id}/versions/{numero}/ecarts")
    public List<ModelePvDto.Ecart> ecarts(@PathVariable Long id, @PathVariable Integer numero) {
        return service.ecarts(id, numero);
    }

    /** Les modèles intégrés au JAR, choisis par le code (« modeles-pv-integres », §B1). */
    @GetMapping("/integres")
    public List<ModelePvDto.Integre> integres() {
        return service.integres();
    }

    /** Le {@code .docx} d'un modèle intégré, tel que livré ; 404 hors de la liste servie (« modeles-pv-integres-consultation », §B1). */
    @GetMapping("/integres/{code}/fichiers/{nom:.+}")
    public ResponseEntity<byte[]> fichierIntegre(@PathVariable String code, @PathVariable String nom) {
        return ResponseEntity.ok().contentType(DOCX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nom, StandardCharsets.UTF_8).build().toString())
                .body(integres.fichier(code, nom));
    }

    /**
     * L'aperçu d'un modèle intégré sur un dossier réel, sans rien enregistrer (« modeles-pv-integres-consultation », §B2) : le fichier
     * choisi par la règle de production est nommé dans {@code X-Fichier-Modele}. {@code pv=} / {@code lettre=} visent un PV ou une
     * lettre du dossier (défaut : le plus récent).
     */
    @PostMapping("/integres/{code}/apercu")
    public ResponseEntity<byte[]> apercuIntegre(@PathVariable String code, @RequestParam Integer dossier,
            @RequestParam(required = false) String avis, @RequestParam(required = false) Integer pv,
            @RequestParam(required = false) Integer lettre, @RequestParam(defaultValue = "pdf") String format) {
        boolean pdf = !"docx".equalsIgnoreCase(format);
        ModelesIntegresService.Apercu a = integres.apercu(code, dossier, avis, pv, lettre, pdf);
        return ResponseEntity.ok().contentType(pdf ? MediaType.APPLICATION_PDF : DOCX)
                .header("X-Fichier-Modele", a.fichier())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename("apercu-" + code + "-dossier-" + dossier + (pdf ? ".pdf" : ".docx"), StandardCharsets.UTF_8).build().toString())
                .body(a.contenu());
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

    /** ⚠️ 2026-10-21 (« modele-pv-page », §B2) — le dépôt d'un {@code .docx} est retiré : 409 {@code SUPPORT_ABANDONNE}. */
    @PostMapping(value = "/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ModelePvDto.Version deposer(@PathVariable Long id, @RequestParam(value = "fichier", required = false) MultipartFile fichier) {
        return service.deposer(id, fichier);
    }

    /**
     * ⚠️ 2026-10-21 (« modele-pv-page », §B2) — une nouvelle version page : {@code { document, motif? }}. Rend la version en brouillon,
     * son document et son rapport de contrôle.
     */
    @PostMapping(value = "/{id}/versions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ModelePvDto.Version enregistrer(@PathVariable Long id, @Valid @RequestBody ModelePvDto.DocumentRequest corps) {
        return service.enregistrerVersion(id, corps);
    }

    /** ⚠️ 2026-10-21 (« modele-pv-page », §B2, Q4) — remplace le document d'un brouillon ; le rapport est recalculé. */
    @PutMapping("/{id}/versions/{numero}/document")
    public ModelePvDto.Version document(@PathVariable Long id, @PathVariable Integer numero,
            @Valid @RequestBody ModelePvDto.DocumentRequest corps) {
        return service.modifierDocument(id, numero, corps);
    }

    /**
     * Le modèle de départ assemblé par l'application (« modele-pv-depart ») : une version en brouillon, conditions et champs
     * déclarés, texte à faire valider par la CNM. Corps facultatif {@code { portee }}.
     */
    @PostMapping("/{id}/versions/depart")
    public ModelePvDto.Version depart(@PathVariable Long id, @RequestBody(required = false) ModelePvDto.DepartRequest corps) {
        return service.depart(id, corps == null ? null : corps.portee(), corps == null ? null : corps.categorie(),
                corps == null ? null : corps.sigle());
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
     * L'aperçu sur un dossier réel, sans rien enregistrer : {@code format=pdf} (défaut) ou {@code docx} (une version Word seulement,
     * ⚠️ 2026-10-21 : une page s'aperçoit en PDF, 400 {@code FORMAT_INDISPONIBLE}) ; {@code avis} force l'avis
     * (FAV, FAVR, DEF, NSP) ; le corps facultatif porte les champs saisis. ⚠️ 2026-10-22 (« apercu-exemple ») — {@code exemple=true}
     * à la place de {@code dossier} : le dossier d'exemple fictif, mention « EXEMPLE » sur chaque page.
     */
    @PostMapping("/{id}/versions/{numero}/apercu")
    public ResponseEntity<byte[]> apercu(@PathVariable Long id, @PathVariable Integer numero, @RequestParam(required = false) Integer dossier,
            @RequestParam(defaultValue = "false") boolean exemple, @RequestParam(required = false) Long procedure,
            @RequestParam(required = false) Integer lot,
            @RequestParam(required = false) String avis, @RequestParam(defaultValue = "pdf") String format,
            @RequestParam(required = false) Integer lettre, @RequestBody(required = false) ModelePvDto.ApercuRequest corps) {
        boolean pdf = !"docx".equalsIgnoreCase(format);
        byte[] contenu = service.apercu(id, numero, dossier, procedure, lot, exemple, avis, lettre, pdf, corps == null ? null : corps.saisis());
        return ResponseEntity.ok().contentType(pdf ? MediaType.APPLICATION_PDF : DOCX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename("apercu-" + id + "-v" + numero + (exemple ? "-exemple" : procedure != null ? "-procedure-" + procedure + "-lot-" + (lot == null ? 1 : lot)
                                : "-dossier-" + dossier) + (pdf ? ".pdf" : ".docx"), StandardCharsets.UTF_8)
                        .build().toString())
                .body(contenu);
    }

    @PostMapping("/{id}/versions/{numero}/mettre-en-vigueur")
    public ModelePvDto.Fiche mettreEnVigueur(@PathVariable Long id, @PathVariable Integer numero,
            @Valid @RequestBody(required = false) ModelePvDto.MotifRequest corps) {
        return service.mettreEnVigueur(id, numero, corps == null ? null : corps.motif(), corps == null ? null : corps.juriste());
    }

    @PutMapping("/{id}/rattachements")
    public ModelePvDto.Fiche rattacher(@PathVariable Long id, @RequestBody ModelePvDto.RattachementsRequest corps) {
        return service.rattacher(id, corps == null ? null : corps.sousTypes(), corps == null ? null : corps.categories(),
                corps == null ? null : corps.sigles());
    }

    @PostMapping("/{id}/archiver")
    public ModelePvDto.Fiche archiver(@PathVariable Long id, @Valid @RequestBody(required = false) ModelePvDto.MotifRequest corps) {
        return service.archiver(id, corps == null ? null : corps.motif());
    }

    /** ⚠️ 2026-10-21 (« modele-pv-suppression », B1) — un brouillon seulement ; rend la fiche. */
    @DeleteMapping("/{id}/versions/{numero}")
    public ModelePvDto.Fiche supprimerVersion(@PathVariable Long id, @PathVariable Integer numero) {
        return service.supprimerVersion(id, numero);
    }

    /** ⚠️ 2026-10-21 (« modele-pv-suppression », B2) — un modèle jamais en vigueur et sans sous-type ; 204. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
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
