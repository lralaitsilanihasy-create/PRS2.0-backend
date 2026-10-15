package cnm.prs.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.PublicationAgpmDto;
import cnm.prs.entity.PublicationAgpm;
import cnm.prs.service.PublicationAgpmService;

/**
 * ⚠️ V118 (2026-10-15, demande front « pièces de l'AMI et publication de l'AGPM », §B2) — la publication de l'AGPM déposée sur le plan
 * de passation contrôlé, et lue depuis le plan ou depuis tout dossier dont la ligne relève du plan.
 */
@RestController
@RequestMapping("/api/dossiers/{idDossier}/publication-agpm")
public class PublicationAgpmController {

    private final PublicationAgpmService service;

    public PublicationAgpmController(PublicationAgpmService service) {
        this.service = service;
    }

    /**
     * Dépôt (multipart : {@code fichier} PDF, {@code journal}, {@code numero} facultatif, {@code dateParution} AAAA-MM-JJ,
     * {@code referenceAgpm}) ; remplace la publication en vigueur du plan. 201 ; 400 nominatif ; 403 ; 409 {@code PAS_UN_PLAN},
     * {@code PLAN_NON_CONTROLE}.
     */
    @PreAuthorize("hasAnyRole('PRMP','UGPM')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PublicationAgpmDto> deposer(@PathVariable Integer idDossier, @RequestParam(required = false) MultipartFile fichier,
            @RequestParam(required = false) String journal, @RequestParam(required = false) String numero,
            @RequestParam(required = false) String dateParution, @RequestParam(required = false) String referenceAgpm) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.deposer(idDossier, fichier, journal, numero, dateParution, referenceAgpm));
    }

    /** La publication en vigueur pour ce dossier (H2 : héritée d'une version précédente du plan) ; 404 sans publication. */
    @GetMapping
    public PublicationAgpmDto lire(@PathVariable Integer idDossier) {
        return service.lire(idDossier);
    }

    /** Le scan du journal en vigueur (PDF). */
    @GetMapping("/contenu")
    public ResponseEntity<byte[]> contenu(@PathVariable Integer idDossier) {
        PublicationAgpm p = service.contenu(idDossier);
        return Telechargements.fichier(p.getNomFichier(), "pdf", p.getContenu());
    }
}
