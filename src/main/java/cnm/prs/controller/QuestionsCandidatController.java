package cnm.prs.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.QuestionCandidatDto;
import cnm.prs.service.QuestionsCandidatService;

/**
 * ⚠️ R7 (manuel révisé pour l'application, lot 2, §B2 ; V109, 2026-10-13) — les questions des candidats, côté PRMP : la liste avec
 * leurs auteurs (lecteurs internes de la fiche), la réponse (PRMP seule, répondre = diffuser), le PDF des questions et réponses.
 * Le côté candidat et la page publique sont sur {@code /api/procedures-en-ligne/{idDmc}/questions}.
 */
@RestController
@RequestMapping("/api/fiches-marche/{idDmc}/questions")
public class QuestionsCandidatController {

    private final QuestionsCandidatService service;

    public QuestionsCandidatController(QuestionsCandidatService service) {
        this.service = service;
    }

    /** Toutes les questions de la procédure, avec leur auteur et la fenêtre (J−10) ; 404 DMC inconnu. */
    @GetMapping
    public QuestionCandidatDto.Questions lister(@PathVariable Long idDmc) {
        return service.lister(idDmc);
    }

    /** Multipart {@code texte} + {@code fichier} facultatif ; 409 {@code DEJA_REPONDUE} ; 403 hors PRMP. */
    @PostMapping(value = "/{id}/reponse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public QuestionCandidatDto.Question repondre(@PathVariable Long idDmc, @PathVariable Long id,
            @RequestParam(value = "texte", required = false) String texte, @RequestPart(value = "fichier", required = false) MultipartFile fichier) {
        return service.repondre(idDmc, id, texte, fichier);
    }

    /** Les questions et réponses en PDF ; 404 sans question. */
    @GetMapping("/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long idDmc) {
        return Telechargements.fichier("questions-reponses_" + idDmc + ".pdf", "application/pdf", service.pdf(idDmc));
    }
}
