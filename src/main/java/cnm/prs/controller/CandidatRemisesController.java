package cnm.prs.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.RemiseDto;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.security.CurrentUser;
import cnm.prs.service.RemiseConcurrenceService;

/**
 * ⚠️ 2026-10-16 (demande front « marchés subséquents », lot MS-2 ; V125) — le titulaire d'un contrat-cadre : les remises en concurrence
 * qui le consultent, la lettre d'invitation, sa réponse en ligne (devis, facture pro forma ou document convenu, jusqu'à l'échéance) et
 * le résultat.
 */
@RestController
@RequestMapping("/api/candidat/remises")
@PreAuthorize("hasRole('CANDIDAT')")
public class CandidatRemisesController {

    private final RemiseConcurrenceService service;

    public CandidatRemisesController(RemiseConcurrenceService service) {
        this.service = service;
    }

    @GetMapping
    public List<RemiseDto.PourTitulaire> lister() {
        return service.mesRemises(moi());
    }

    /** La lettre d'invitation, en PDF ou en Word ({@code ?format=docx}) ; 404 hors des remises qui le consultent. */
    @GetMapping("/{id}/lettre")
    public ResponseEntity<byte[]> lettre(@PathVariable Long id, @RequestParam(required = false) String format) {
        boolean docx = "docx".equalsIgnoreCase(format);
        return Telechargements.fichier("lettre-invitation-remise-" + id + (docx ? ".docx" : ".pdf"), docx ? "docx" : "pdf",
                service.lettreDuTitulaire(moi(), id, docx));
    }

    /**
     * Multipart {@code nature}, {@code montantHt}, {@code montantTtc}, {@code delai}, {@code fichier} ; 201 ; une nouvelle réponse
     * remplace la précédente ; 400 {@code NATURE_INVALIDE}, {@code MONTANT_INVALIDE}, {@code FICHIER_OBLIGATOIRE}, {@code FORMAT_INVALIDE} ;
     * 409 {@code ECHEANCE_PASSEE}, {@code REMISE_CLOSE}.
     */
    @PostMapping(value = "/{id}/reponse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RemiseDto.PourTitulaire> deposer(@PathVariable Long id, @RequestParam(value = "nature", required = false) String nature,
            @RequestParam(value = "montantHt", required = false) BigDecimal montantHt,
            @RequestParam(value = "montantTtc", required = false) BigDecimal montantTtc,
            @RequestParam(value = "delai", required = false) String delai,
            @RequestPart(value = "fichier", required = false) MultipartFile fichier) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.deposer(moi(), id, nature, montantHt, montantTtc, delai, fichier));
    }

    private static String moi() {
        return CurrentUser.ref().orElseThrow(() -> new ResourceNotFoundException("Compte candidat introuvable."));
    }
}
