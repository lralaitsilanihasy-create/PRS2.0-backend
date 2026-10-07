package cnm.prs.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.EvaluationDto;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.security.CurrentUser;
import cnm.prs.service.EvaluationService;

/**
 * ⚠️ 2026-10-07 (évaluation des offres, §B2, art. 35-VI) — les demandes de précisions reçues par le candidat pour son offre, et sa
 * réponse. Le candidat ne voit que ses demandes (P5).
 */
@RestController
@RequestMapping("/api/candidat/offres/{idOffre}/precisions")
@PreAuthorize("hasRole('CANDIDAT')")
public class CandidatPrecisionsController {

    private final EvaluationService service;

    public CandidatPrecisionsController(EvaluationService service) {
        this.service = service;
    }

    @GetMapping
    public List<EvaluationDto.Demande> lister(@PathVariable String idOffre) {
        return service.precisionsDuCandidat(moi(), idOffre);
    }

    /** Multipart : {@code texte} obligatoire, {@code fichier} facultatif ; 409 {@code DELAI_DEPASSE}, {@code DEJA_REPONDU}. */
    @PostMapping(value = "/{idDemande}/reponse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EvaluationDto.Demande repondre(@PathVariable String idOffre, @PathVariable Long idDemande,
            @RequestParam(value = "texte", required = false) String texte, @RequestPart(value = "fichier", required = false) MultipartFile fichier) {
        return service.repondre(moi(), idOffre, idDemande, texte, fichier);
    }

    private static String moi() {
        return CurrentUser.ref().orElseThrow(() -> new ResourceNotFoundException("Compte candidat introuvable."));
    }
}
