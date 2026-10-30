package cnm.prs.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.PublicationAvisDto;
import cnm.prs.entity.PublicationAvis;
import cnm.prs.service.PublicationAvisService;

/**
 * ⚠️ V154 (2026-10-30, demande front « pieces-ddm-donnees », §B2) — la publication de l'avis déposée une fois sur la procédure, et lue
 * depuis la procédure ou depuis un dossier qui en relève (dossier né de la fiche, dossier de marché de l'attribution).
 */
@RestController
public class PublicationAvisController {

    private final PublicationAvisService service;

    public PublicationAvisController(PublicationAvisService service) {
        this.service = service;
    }

    /**
     * Dépôt (multipart : {@code fichier} PDF, {@code journal} — obligatoire hors prestations intellectuelles —, {@code numero} facultatif,
     * {@code dateParution} AAAA-MM-JJ) ; remplace la publication en vigueur. 201 ; 400 nominatif ; 403 ; 409 {@code FICHE_NON_VALIDEE},
     * {@code AVIS_NON_PUBLIE}.
     */
    @PreAuthorize("hasAnyRole('PRMP','UGPM')")
    @PostMapping(path = "/api/fiches-marche/{idDmc}/publication-avis", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PublicationAvisDto> deposer(@PathVariable Long idDmc, @RequestParam(required = false) MultipartFile fichier,
            @RequestParam(required = false) String journal, @RequestParam(required = false) String numero,
            @RequestParam(required = false) String dateParution) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.deposer(idDmc, fichier, journal, numero, dateParution));
    }

    /** La publication en vigueur de la procédure ; 404 {@code PUBLICATION_AVIS_ABSENTE}. */
    @GetMapping("/api/fiches-marche/{idDmc}/publication-avis")
    public PublicationAvisDto lire(@PathVariable Long idDmc) {
        return service.lire(idDmc);
    }

    /** Le scan en vigueur (PDF). */
    @GetMapping("/api/fiches-marche/{idDmc}/publication-avis/contenu")
    public ResponseEntity<byte[]> contenu(@PathVariable Long idDmc) {
        PublicationAvis p = service.contenu(idDmc);
        return Telechargements.fichier(p.getNomFichier(), "pdf", p.getContenu());
    }

    /** La publication de la procédure dont relève le dossier ; 404 sans procédure ou sans publication. */
    @GetMapping("/api/dossiers/{idDossier}/publication-avis")
    public PublicationAvisDto lireDuDossier(@PathVariable Integer idDossier) {
        return service.lireDuDossier(idDossier);
    }

    /** Le scan, lu depuis le dossier (lien de la pièce fournie {@code PUBLICATION_AVIS} du dossier de marché). */
    @GetMapping("/api/dossiers/{idDossier}/publication-avis/contenu")
    public ResponseEntity<byte[]> contenuDuDossier(@PathVariable Integer idDossier) {
        PublicationAvis p = service.contenuDuDossier(idDossier);
        return Telechargements.fichier(p.getNomFichier(), "pdf", p.getContenu());
    }
}
