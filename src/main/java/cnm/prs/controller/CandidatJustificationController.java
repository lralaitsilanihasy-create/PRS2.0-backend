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
 * ⚠️ 2026-10-07 (évaluation des offres, tranche 1c, §B4, art. 48) — la demande de justification du prix reçue par le candidat pour son
 * offre, et sa réponse (mêmes règles que les précisions : texte obligatoire, un fichier PDF, JPEG ou PNG, une réponse, dans le délai).
 */
@RestController
@RequestMapping("/api/candidat/offres/{idOffre}/justification")
@PreAuthorize("hasRole('CANDIDAT')")
public class CandidatJustificationController {

    private final EvaluationService service;

    public CandidatJustificationController(EvaluationService service) {
        this.service = service;
    }

    @GetMapping
    public List<EvaluationDto.Demande> lister(@PathVariable String idOffre) {
        return service.justificationsDuCandidat(moi(), idOffre);
    }

    /** Multipart : {@code texte} obligatoire, {@code fichier} facultatif ; 404 sans demande ; 409 {@code DELAI_DEPASSE}, {@code DEJA_REPONDU}. */
    @PostMapping(value = "/reponse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EvaluationDto.Demande repondre(@PathVariable String idOffre, @RequestParam(value = "texte", required = false) String texte,
            @RequestPart(value = "fichier", required = false) MultipartFile fichier) {
        return service.repondreJustification(moi(), idOffre, texte, fichier);
    }

    private static String moi() {
        return CurrentUser.ref().orElseThrow(() -> new ResourceNotFoundException("Compte candidat introuvable."));
    }
}
