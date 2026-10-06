package cnm.prs.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.SpecificationsDto;
import cnm.prs.entity.SpecificationsFiche;
import cnm.prs.service.SpecificationsService;

/**
 * ⚠️ 2026-10-06 (demande front « le DAO complet », §B2) — les <strong>spécifications techniques</strong> d'une fiche DAO : un
 * {@code .docx} joint à la version (lecture de la fiche ; écriture PRMP, UGPM, Administrateur sur le brouillon), inséré au DAO
 * complet.
 */
@RestController
@RequestMapping("/api/fiches-marche/{idDmc}/specifications")
public class SpecificationsController {

    private final SpecificationsService service;

    public SpecificationsController(SpecificationsService service) {
        this.service = service;
    }

    /** 404 sans fichier. */
    @GetMapping
    public SpecificationsDto lire(@PathVariable Long idDmc) {
        return service.lire(idDmc);
    }

    /** 400 {@code FICHIER_ABSENT} / {@code FORMAT_INVALIDE} ; 403 ; 409 {@code FICHE_VALIDEE} ; 413 au-delà de 20 Mo. */
    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SpecificationsDto deposer(@PathVariable Long idDmc, @RequestPart(value = "fichier", required = false) MultipartFile fichier) {
        return service.deposer(idDmc, fichier);
    }

    @DeleteMapping
    public ResponseEntity<Void> retirer(@PathVariable Long idDmc) {
        service.retirer(idDmc);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/fichier")
    public ResponseEntity<byte[]> fichier(@PathVariable Long idDmc) {
        SpecificationsFiche s = service.fichier(idDmc);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, Telechargements.disposition(s.getNomFichier()))
                .contentType(Telechargements.typeAutorise("docx")).body(s.getContenu());
    }
}
