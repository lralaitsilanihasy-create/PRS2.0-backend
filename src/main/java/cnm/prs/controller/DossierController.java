package cnm.prs.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import jakarta.validation.Valid;

import cnm.prs.dto.ActionDossierDto;
import cnm.prs.dto.DossierDto;
import cnm.prs.dto.DossierResoumissionRequest;
import cnm.prs.dto.EchangeDto;
import cnm.prs.dto.PpmDto;
import cnm.prs.service.DossierService;
import cnm.prs.service.PpmService;

/**
 * Contrôleur REST pour la ressource {@code dossiers} (table {@code t_dossier}).
 */
@RestController
@RequestMapping("/api/dossiers")
public class DossierController {

    private final DossierService service;
    private final PpmService ppmService;

    public DossierController(DossierService service, PpmService ppmService) {
        this.service = service;
        this.ppmService = ppmService;
    }

    /**
     * Dossiers visibles dans le périmètre de l'appelant (§1), filtrables par statut côté serveur
     * via {@code ?statut=SOUMIS} (statut inconnu → 400). Pour la file « à réceptionner » du
     * Secrétaire, préférer {@code /api/dossiers/a-receptionner} (SOUMIS + sans réception, sans N+1).
     */
    @GetMapping
    public List<DossierDto> findAll(@RequestParam(required = false) String statut,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String sousType) {
        return service.findAll(statut, type, sousType);
    }

    /** File « à réceptionner » du Secrétaire (§3.4) : dossiers SOUMIS de sa localité sans réception. */
    @PreAuthorize("@perm.peutExercer('SECRETAIRE') or hasRole('ADMINISTRATEUR')")
    @GetMapping("/a-receptionner")
    public List<DossierDto> aReceptionner() {
        return service.aReceptionner();
    }

    /** File « à examiner » du Membre attributaire (§2.4) : ses dossiers DISPATCHE, pas encore examinés. */
    @PreAuthorize("@perm.peutExercer('MEMBRE') or hasRole('ADMINISTRATEUR')")
    @GetMapping("/a-examiner")
    public List<DossierDto> aExaminer() {
        return service.aExaminer();
    }

    /** Historique « examinés » du Membre attributaire (EXAMINE + PV_SIGNE + CLOTURE), paginé. */
    @PreAuthorize("@perm.peutExercer('MEMBRE') or hasRole('ADMINISTRATEUR')")
    @GetMapping("/examines")
    public Page<DossierDto> examines(Pageable pageable) {
        return service.examines(pageable);
    }

    /** File « à vérifier » du Vérificateur (§3.6) : dossiers EN_VERIFICATION de sa localité. */
    @PreAuthorize("@perm.peutExercer('VERIFICATEUR') or hasRole('ADMINISTRATEUR')")
    @GetMapping("/a-verifier")
    public List<DossierDto> aVerifier() {
        return service.aVerifier();
    }

    /** Historique « vérifiés / clôturés » du Vérificateur (PV signés clôturés), paginé, lecture seule. */
    @PreAuthorize("@perm.peutExercer('VERIFICATEUR') or hasRole('ADMINISTRATEUR')")
    @GetMapping("/verifies")
    public Page<DossierDto> verifies(Pageable pageable) {
        return service.verifies(pageable);
    }

    /** File « En attente PRMP » du Vérificateur (lecture seule) : dossiers EN_ATTENTE_DECISION_PRMP de sa localité. */
    @PreAuthorize("@perm.peutExercer('VERIFICATEUR') or hasRole('ADMINISTRATEUR')")
    @GetMapping("/en-attente-prmp")
    public List<DossierDto> enAttentePrmp() {
        return service.enAttentePrmp();
    }

    /** Liste déroulante « dossiers retirables » de la PRMP (SOUMIS/PRET_DISPATCH dont elle est propriétaire). */
    @PreAuthorize("hasRole('PRMP')")
    @GetMapping("/retirables")
    public List<DossierDto> retirables() {
        return service.retirables();
    }

    @GetMapping("/{id}")
    public DossierDto findById(@PathVariable Integer id) {
        return service.findById(id);
    }

    /**
     * Résout le PPM rattaché au dossier (mapping {@code idDossier → PPM}), y compris pour un dossier
     * <strong>BROUILLON</strong> lu par son propriétaire — permet d'ouvrir un brouillon depuis
     * « Mes brouillons » même sans aucun marché. Aucun PPM rattaché → 404 ; hors périmètre → 403.
     */
    @GetMapping("/{id}/ppm")
    public PpmDto ppmDuDossier(@PathVariable Integer id) {
        return ppmService.findByDossier(id);
    }

    // Création/édition brutes verrouillées : la saisie passe par la façade /api/saisies (PRMP) ; ici réservé Admin.
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PostMapping
    public ResponseEntity<DossierDto> create(@Valid @RequestBody DossierDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PutMapping("/{id}")
    public DossierDto update(@PathVariable Integer id, @Valid @RequestBody DossierDto dto) {
        return service.update(id, dto);
    }

    // Suppression d'un dossier brouillon : PRMP/UGPM propriétaire (garde statut/propriété/cascade en service).
    @PreAuthorize("hasAnyRole('PRMP','UGPM')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Soumission officielle d'un dossier par la PRMP (§3.1, Module 03) : génère la référence
     * unique et notifie le Secrétaire/CC de la localité. Réservé au profil {@code PRMP}.
     */
    @PreAuthorize("hasRole('PRMP')")
    @PostMapping("/{id}/soumettre")
    public DossierDto soumettre(@PathVariable Integer id) {
        return service.soumettre(id);
    }

    /** Resoumission PRMP d'un dossier rectifié (EN_ATTENTE_DECISION_PRMP → EN_VERIFICATION). Motif obligatoire. */
    @PreAuthorize("hasRole('PRMP')")
    @PostMapping("/{id}/resoumettre")
    public DossierDto resoumettre(@PathVariable Integer id, @Valid @RequestBody DossierResoumissionRequest req) {
        return service.resoumettre(id, req.motifRectification());
    }

    /** ⚠️ Spec navette (2026-08-01, cas 3) — compléments de lettre de renvoi transmis (EN_ATTENTE_PIECES → EXAMINE). */
    @PreAuthorize("hasRole('PRMP')")
    @PostMapping("/{id}/transmettre-complements")
    public DossierDto transmettreComplements(@PathVariable Integer id) {
        return service.transmettreComplements(id);
    }

    /** ⚠️ Spec recevabilité (2026-08-02) — signalement des pièces manquantes au DÉPÔT (SOUMIS → EN_ATTENTE_COMPLEMENTS_DEPOT). */
    @PreAuthorize("hasRole('SECRETAIRE')")
    @PostMapping("/{id}/signaler-pieces-manquantes")
    public DossierDto signalerPiecesManquantes(@PathVariable Integer id) {
        return service.signalerPiecesManquantes(id);
    }

    /** ⚠️ Spec recevabilité (2026-08-02) — compléments de DÉPÔT transmis par la PRMP (EN_ATTENTE_COMPLEMENTS_DEPOT → SOUMIS). */
    @PreAuthorize("hasRole('PRMP')")
    @PostMapping("/{id}/transmettre-complements-depot")
    public DossierDto transmettreComplementsDepot(@PathVariable Integer id) {
        return service.transmettreComplementsDepot(id);
    }

    /** Historique d'échanges d'un dossier clôturé (observations + rectifications PRMP), trié date ASC. */
    @PreAuthorize("hasRole('PRMP') or @perm.peutExercer('VERIFICATEUR') or hasRole('ADMINISTRATEUR')")
    @GetMapping("/{id}/historique-echanges")
    public List<EchangeDto> historiqueEchanges(@PathVariable Integer id) {
        return service.historiqueEchanges(id);
    }

    /**
     * ⚠️ Règle ajoutée (spec « Mandats PRMP ») — journal des actions du dossier : opérateur courant, mandat
     * sous lequel il a agi, horodatage. Ouvert aux profils concernés (PRMP / UGPM propriétaires du
     * périmètre, contrôleurs de la localité, Président, Administrateur) — le périmètre de visibilité du
     * dossier (§1) s'applique, un accès hors périmètre reste refusé (403).
     */
    @GetMapping("/{id}/journal")
    public List<ActionDossierDto> journal(@PathVariable Integer id) {
        return service.journal(id);
    }
}
