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
    private final cnm.prs.service.ImportDaoService importDao;
    private final cnm.prs.service.AvisSpecifiqueService avis;
    private final cnm.prs.service.LettreInvitationService lettres;
    private final cnm.prs.service.ProceduresEnLigneService enLigne;

    public FicheMarcheController(FicheMarcheService service, FicheMarcheDossierService dossiers,
            cnm.prs.service.ImportDaoService importDao, cnm.prs.service.AvisSpecifiqueService avis,
            cnm.prs.service.LettreInvitationService lettres, cnm.prs.service.ProceduresEnLigneService enLigne) {
        this.enLigne = enLigne;
        this.avis = avis;
        this.lettres = lettres;
        this.importDao = importDao;
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

    /**
     * ⚠️ 2026-10-04 (soumission en ligne, lot 1c, §B8) — le registre des retraits du DAO en ligne : PRMP de la fiche seule
     * (403 autre profil ou hors périmètre), 404 DMC inconnu ; du plus ancien au plus récent.
     */
    @PreAuthorize("hasRole('PRMP')")
    @GetMapping("/{idDmc}/retraits")
    public List<cnm.prs.dto.ProcedureEnLigneDto.Retrait> retraits(@PathVariable Long idDmc) {
        return enLigne.retraits(idDmc);
    }

    /**
     * ⚠️ Avis spécifique d'appel d'offres (demande front du 2026-09-30, §B3) — imprime l'avis sur la dernière version
     * validée : 201 et la paire .docx / .pdf produite (type {@code AVIS}) ; 400 nominatif ; 409 {@code AVIS_INDISPONIBLE}.
     * PRMP et UGPM (gardes dans {@link cnm.prs.service.AvisSpecifiqueService}).
     */
    @PostMapping("/{idDmc}/avis-specifique")
    @PreAuthorize("hasAnyRole('PRMP', 'UGPM')")
    public ResponseEntity<List<DocumentFicheDto>> imprimerAvis(@PathVariable Long idDmc,
            @RequestBody(required = false) cnm.prs.dto.AvisSpecifiqueRequest corps) {
        return ResponseEntity.status(HttpStatus.CREATED).body(avis.produire(idDmc, corps));
    }

    /** ⚠️ Avis spécifique (§B4) — l'avis peut-il être imprimé, et sinon pourquoi. PRMP et UGPM. */
    @GetMapping("/{idDmc}/avis-specifique/disponibilite")
    @PreAuthorize("hasAnyRole('PRMP', 'UGPM')")
    public cnm.prs.dto.AvisDisponibiliteDto disponibiliteAvis(@PathVariable Long idDmc) {
        return avis.disponibilite(idDmc);
    }

    /**
     * ⚠️ 2026-10-01 (lot AV-4.1, §B3) — imprime une lettre d'invitation par candidat de la liste restreinte (prestations
     * intellectuelles), sur la dernière version validée : 201 et les paires .docx / .pdf produites (type
     * {@code LETTRE_INVITATION}) ; 400 nominatif ; 409 {@code LETTRE_INDISPONIBLE}. PRMP et UGPM.
     */
    @PostMapping("/{idDmc}/lettres-invitation")
    @PreAuthorize("hasAnyRole('PRMP', 'UGPM')")
    public ResponseEntity<List<DocumentFicheDto>> imprimerLettres(@PathVariable Long idDmc,
            @RequestBody(required = false) cnm.prs.dto.LettreInvitationRequest corps) {
        return ResponseEntity.status(HttpStatus.CREATED).body(lettres.produire(idDmc, corps));
    }

    /** ⚠️ 2026-10-01 (lot AV-4.1, §B4) — les lettres peuvent-elles être imprimées, et sinon pourquoi. PRMP et UGPM. */
    @GetMapping("/{idDmc}/lettres-invitation/disponibilite")
    @PreAuthorize("hasAnyRole('PRMP', 'UGPM')")
    public cnm.prs.dto.AvisDisponibiliteDto disponibiliteLettres(@PathVariable Long idDmc) {
        return lettres.disponibilite(idDmc);
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

    /**
     * ⚠️ Import du DAO (demande front du 2026-09-28, §B1 ; ADR-0012) — lit un DAO Word (part multipart {@code fichier})
     * et propose de quoi pré-remplir la fiche, sans rien écrire. 415 {@code FORMAT_NON_SUPPORTE} hors {@code .docx} ;
     * 409 {@code FICHE_VALIDEE} ; 422 {@code MODELE_ABSENT} ; 403 hors PRMP propriétaire et UGPM.
     */
    @PreAuthorize("hasAnyRole('PRMP', 'UGPM')")
    @PostMapping(path = "/{idDmc}/import", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public cnm.prs.dto.ImportDaoResult importer(@PathVariable Long idDmc,
            @RequestParam("fichier") org.springframework.web.multipart.MultipartFile fichier) throws java.io.IOException {
        return importDao.lire(idDmc, fichier.getOriginalFilename(), fichier.getBytes());
    }

    /**
     * ⚠️ Import du DAO (§B2) — écrit d'un seul coup les lignes retenues : fusion (rien n'est effacé), atomique (400
     * nominatif et rien d'écrit au premier refus), journal {@code FICHE_IMPORTEE}. Rend la fiche.
     */
    @PreAuthorize("hasAnyRole('PRMP', 'UGPM')")
    @PutMapping("/{idDmc}/import/appliquer")
    public FicheMarcheDto appliquerImport(@PathVariable Long idDmc, @RequestBody cnm.prs.dto.ImportDaoAppliquerRequest corps) {
        return service.appliquerImport(idDmc, corps.cadrage(), corps.valeurs(), corps.fichier(), corps.empreinte());
    }

    /** Les valeurs d'un bloc — remplace celles du bloc ; 400 nominatif par champ fautif. */
    @PutMapping("/{idDmc}/blocs/{bloc}")
    public FicheMarcheDto bloc(@PathVariable Long idDmc, @PathVariable String bloc,
            @Valid @RequestBody BlocValeursRequest corps) {
        return service.ecrireBloc(idDmc, bloc, corps.valeurs());
    }

    /**
     * ⚠️ V45 (2026-09-25, formulaires du candidat, §B1) — le besoin de la version courante (fournitures), trié par lot
     * puis ordre, avec les caractéristiques de chaque article. Même lecture que la fiche.
     */
    @GetMapping("/{idDmc}/articles")
    public List<cnm.prs.dto.ArticleBesoinDto> articles(@PathVariable Long idDmc) {
        return service.articles(idDmc);
    }

    /**
     * ⚠️ V45 — remplacement en bloc du besoin d'un lot ({@code ?lot=n}) ou de toute la fiche, corps
     * {@code {"articles":[…]}} ; renvoie tout le besoin. 400 nominatif ({@code articles[i].…}, {@code lot}), 409
     * {@code FICHE_VALIDEE} / {@code BESOIN_HORS_PERIMETRE}. Mêmes profils que l'écriture de la fiche.
     */
    @PutMapping("/{idDmc}/articles")
    public List<cnm.prs.dto.ArticleBesoinDto> remplacerArticles(@PathVariable Long idDmc,
            @RequestParam(required = false) Integer lot, @RequestBody cnm.prs.dto.ArticleBesoinDto.Remplacement corps) {
        return service.remplacerArticles(idDmc, lot, corps == null ? null : corps.getArticles());
    }

    /** ⚠️ V60 (2026-10-03, §B1.1) — le matériel exigé de la version courante (travaux), dans l'ordre. */
    @GetMapping("/{idDmc}/materiel")
    public List<cnm.prs.dto.MaterielExigeDto> materiel(@PathVariable Long idDmc) {
        return service.materiel(idDmc);
    }

    /**
     * ⚠️ V60 — remplacement de toute la liste du matériel, corps {@code {"materiel":[…]}} ; l'ordre est la position. 400
     * nominatif ({@code materiel[i].…}), 409 {@code FICHE_VALIDEE} / {@code MOYENS_HORS_PERIMETRE}.
     */
    @PutMapping("/{idDmc}/materiel")
    public List<cnm.prs.dto.MaterielExigeDto> remplacerMateriel(@PathVariable Long idDmc,
            @RequestBody cnm.prs.dto.MaterielExigeDto.Remplacement corps) {
        return service.remplacerMateriel(idDmc, corps == null ? null : corps.getMateriel());
    }

    /** ⚠️ V60 — le personnel clé exigé de la version courante (travaux), dans l'ordre. */
    @GetMapping("/{idDmc}/personnel")
    public List<cnm.prs.dto.PersonnelExigeDto> personnel(@PathVariable Long idDmc) {
        return service.personnel(idDmc);
    }

    /** ⚠️ V60 — remplacement de toute la liste du personnel, corps {@code {"personnel":[…]}} ; mêmes statuts. */
    @PutMapping("/{idDmc}/personnel")
    public List<cnm.prs.dto.PersonnelExigeDto> remplacerPersonnel(@PathVariable Long idDmc,
            @RequestBody cnm.prs.dto.PersonnelExigeDto.Remplacement corps) {
        return service.remplacerPersonnel(idDmc, corps == null ? null : corps.getPersonnel());
    }

    /** ⚠️ V61 (2026-10-03, §B1.1) — les pièces exigées de la version courante (travaux), dans l'ordre. */
    @GetMapping("/{idDmc}/pieces")
    public List<cnm.prs.dto.PieceExigeeDto> pieces(@PathVariable Long idDmc) {
        return service.pieces(idDmc);
    }

    /**
     * ⚠️ V61 — remplacement de toute la liste des pièces, corps {@code {"pieces":[…]}} ; l'ordre est la position. 400
     * nominatif ({@code pieces[i].…}), 409 {@code FICHE_VALIDEE} / {@code PIECES_HORS_PERIMETRE}.
     */
    @PutMapping("/{idDmc}/pieces")
    public List<cnm.prs.dto.PieceExigeeDto> remplacerPieces(@PathVariable Long idDmc,
            @RequestBody cnm.prs.dto.PieceExigeeDto.Remplacement corps) {
        return service.remplacerPieces(idDmc, corps == null ? null : corps.getPieces());
    }

    /** ⚠️ V45 — retire un article (et ses caractéristiques) ; 404 s'il n'appartient pas à la version courante. */
    @org.springframework.web.bind.annotation.DeleteMapping("/{idDmc}/articles/{idArticle}")
    public ResponseEntity<Void> supprimerArticle(@PathVariable Long idDmc, @PathVariable Integer idArticle) {
        service.supprimerArticle(idDmc, idArticle);
        return ResponseEntity.noContent().build();
    }

    /**
     * ⚠️ 2026-09-26 (demande front du 2026-09-25, « défaire une fiche marché ouverte par erreur ») — supprime une fiche
     * sans historique et son DMC : la ligne redevient préparable. 204 ; 403 hors PRMP / UGPM propriétaires ; 404 ; 409
     * {@code FICHE_VALIDEE}, {@code FICHE_AVEC_HISTORIQUE}, {@code FICHE_AVEC_DOCUMENTS}, {@code FICHE_AVEC_DOSSIER}
     * (avec {@code idDossier}).
     */
    @org.springframework.web.bind.annotation.DeleteMapping("/{idDmc}")
    public ResponseEntity<Void> supprimer(@PathVariable Long idDmc) {
        service.supprimer(idDmc);
        return ResponseEntity.noContent().build();
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
