package cnm.prs.controller;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.util.WebUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import cnm.prs.dto.DossierDto;
import cnm.prs.dto.EditionPpmRequest;
import cnm.prs.dto.SaisieDossierRequest;
import cnm.prs.dto.SaisiePpmImportResult;
import cnm.prs.dto.SaisiePpmRequest;
import cnm.prs.service.SaisiePpmImportService;
import cnm.prs.service.SaisieService;

/**
 * Façade de saisie d'un dossier à soumettre (§3.1, Module 02) — réservée au profil {@code PRMP}.
 * « Saisir un PPM/DAO/MAOO » crée le dossier (BROUILLON) et son contenu en une transaction.
 */
@RestController
@RequestMapping("/api/saisies")
public class SaisieController {

    private final SaisieService service;
    private final SaisiePpmImportService importService;

    public SaisieController(SaisieService service, SaisiePpmImportService importService) {
        this.service = service;
        this.importService = importService;
    }

    /**
     * Import <strong>read-only</strong> d'un PPM PDF pour pré-remplir le formulaire de saisie : parse le
     * PDF (part {@code fichier}) et renvoie les données extraites — <strong>ne crée rien</strong>. La
     * création reste {@code POST /api/saisies/ppm}. PDF illisible/non conforme → 400.
     */
    @PreAuthorize("hasAnyRole('PRMP','UGPM')")
    @PostMapping(value = "/ppm/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SaisiePpmImportResult importerPpm(@RequestPart("fichier") MultipartFile fichier) {
        return importService.importer(fichier);
    }

    /** Saisie d'un PPM (dossier PPM + PPM + lignes de marché). */
    @PreAuthorize("hasAnyRole('PRMP','UGPM')")
    @PostMapping(value = "/ppm", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DossierDto> saisirPpm(@Valid @RequestBody SaisiePpmRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.saisirPpm(req));
    }

    /**
     * Saisie d'un PPM avec ses pièces jointes initiales (multipart). Part {@code data} = JSON
     * {@link SaisiePpmRequest} ; parts fichiers nommés {@code piece_<idTypePiece>}. Chaque pièce est
     * persistée avec {@code apresLettreRenvoi=false}.
     */
    @PreAuthorize("hasAnyRole('PRMP','UGPM')")
    @PostMapping(value = "/ppm", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DossierDto> saisirPpmAvecPieces(
            @Valid @RequestPart("data") SaisiePpmRequest req, HttpServletRequest request) {
        Map<Integer, MultipartFile> pieces = new HashMap<>();
        // Déballe la requête native : sous Spring Security, `request` est un wrapper qui n'est pas
        // un MultipartHttpServletRequest (un simple instanceof échouerait et perdrait les pièces).
        MultipartHttpServletRequest multipart =
                WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class);
        if (multipart != null) {
            for (Iterator<String> it = multipart.getFileNames(); it.hasNext();) {
                String nom = it.next();
                if (nom != null && nom.startsWith("piece_")) {
                    try {
                        Integer idTypePiece = Integer.valueOf(nom.substring("piece_".length()));
                        pieces.put(idTypePiece, multipart.getFile(nom));
                    } catch (NumberFormatException ignore) {
                        // part fichier au nom non conforme : ignorée (pas un piece_<idTypePiece>)
                    }
                }
            }
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(service.saisirPpm(req, pieces));
    }

    /** Saisie d'un dossier sans contenu (DAO, MAOO, …). */
    @PreAuthorize("hasAnyRole('PRMP','UGPM')")
    @PostMapping("/dossier")
    public ResponseEntity<DossierDto> saisirDossier(@Valid @RequestBody SaisieDossierRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.saisirDossier(req));
    }

    /** Édition d'un brouillon PPM : en-tête + réconciliation des lignes de marché, en une transaction. */
    @PreAuthorize("hasAnyRole('PRMP','UGPM')")
    @PutMapping("/ppm/{idDossier}")
    public DossierDto editerPpm(@PathVariable Integer idDossier, @Valid @RequestBody EditionPpmRequest req) {
        return service.editerPpm(idDossier, req);
    }
}
