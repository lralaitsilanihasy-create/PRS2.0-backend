package cnm.prs.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.CaoDto;
import cnm.prs.service.CaoService;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 2a, §B1 ; Q11 du pilote) — la <strong>commission d'appel
 * d'offres</strong> d'un DAO, sous la fiche : lecture par qui lit la fiche, écriture par la PRMP de la fiche seule (gardes
 * dans le service).
 */
@RestController
@RequestMapping("/api/fiches-marche/{idDmc}/cao")
public class CaoController {

    private final CaoService service;

    public CaoController(CaoService service) {
        this.service = service;
    }

    @GetMapping
    public CaoDto lire(@PathVariable Long idDmc) {
        return service.lire(idDmc);
    }

    /** 400 par champ ; 409 {@code MEMBRE_EXCLU} / {@code CEREMONIE_CLOSE}. */
    @PutMapping
    public CaoDto ecrire(@PathVariable Long idDmc, @RequestBody CaoDto.Corps corps) {
        return service.ecrire(idDmc, corps);
    }

    /** Le PDF de la décision signée : 400 (pas un PDF), 404 sans CAO, 413. */
    @PostMapping(value = "/decision", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CaoDto decision(@PathVariable Long idDmc, @RequestPart("fichier") MultipartFile fichier) {
        return service.decision(idDmc, fichier);
    }

    /** Renvoie l'invitation d'un membre : 409 {@code COMPTE_ACTIF}. */
    @PostMapping("/membres/{id}/inviter")
    public CaoDto.Membre inviter(@PathVariable Long idDmc, @PathVariable Long id) {
        return service.inviter(idDmc, id);
    }
}
