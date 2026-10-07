package cnm.prs.controller;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.EvaluationDto;
import cnm.prs.entity.EvaluationDemande;
import cnm.prs.service.EvaluationService;

/**
 * ⚠️ 2026-10-07 (demande front « évaluation des offres », lot 1, tranche 1a ; V76) — l'évaluation d'une procédure en remise
 * électronique, après le PV d'ouverture. Les gardes sont dans le service, par identité : le responsable ouvre, les membres de la CAO
 * déclarés sans conflit décident, le président arrête et rouvre les étapes, la PRMP demande les précisions, la PRMP et l'UGPM lisent.
 */
@RestController
@RequestMapping("/api/fiches-marche/{idDmc}/evaluation")
public class EvaluationController {

    private final EvaluationService service;

    public EvaluationController(EvaluationService service) {
        this.service = service;
    }

    /** L'évaluation ; 404 tant qu'elle n'est pas ouverte. */
    @GetMapping
    public EvaluationDto lire(@PathVariable Long idDmc) {
        return service.lire(idDmc);
    }

    /** 201 ; 409 {@code SEANCE_NON_CLOSE}, {@code EVALUATION_DEJA_OUVERTE}. */
    @PostMapping("/ouvrir")
    public ResponseEntity<EvaluationDto> ouvrir(@PathVariable Long idDmc) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.ouvrir(idDmc));
    }

    /** La déclaration préalable du membre appelant ; 409 {@code DEJA_DECLARE}. */
    @PostMapping("/declaration")
    public EvaluationDto declarer(@PathVariable Long idDmc, @RequestBody(required = false) EvaluationDto.DeclarationRequest corps) {
        return service.declarer(idDmc, corps);
    }

    @PostMapping("/lots/{lot}/etapes/{etape}/arreter")
    public EvaluationDto arreter(@PathVariable Long idDmc, @PathVariable Integer lot, @PathVariable String etape,
            @RequestBody(required = false) EvaluationDto.Arret corps) {
        return service.arreter(idDmc, lot, etape, corps);
    }

    @PostMapping("/lots/{lot}/etapes/{etape}/rouvrir")
    public EvaluationDto rouvrir(@PathVariable Long idDmc, @PathVariable Integer lot, @PathVariable String etape,
            @RequestBody(required = false) EvaluationDto.Reouverture corps) {
        return service.rouvrir(idDmc, lot, etape, corps);
    }

    @PutMapping("/offres/{idOffre}/conformite")
    public EvaluationDto conformite(@PathVariable Long idDmc, @PathVariable String idOffre, @RequestBody EvaluationDto.ConformiteRequest corps) {
        return service.conformite(idDmc, idOffre, corps);
    }

    /** 201 ; PRMP de la fiche (art. 35-VI). */
    @PostMapping("/offres/{idOffre}/precisions")
    public ResponseEntity<EvaluationDto.Demande> demanderPrecisions(@PathVariable Long idDmc, @PathVariable String idOffre,
            @RequestBody EvaluationDto.DemandeRequest corps) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.demanderPrecisions(idDmc, idOffre, corps));
    }

    @GetMapping("/offres/{idOffre}/precisions")
    public List<EvaluationDto.Demande> precisions(@PathVariable Long idDmc, @PathVariable String idOffre) {
        return service.precisions(idDmc, idOffre);
    }

    /** Le fichier joint à une réponse de candidat ; 404 sans fichier. */
    @GetMapping("/demandes/{idDemande}/fichier")
    public ResponseEntity<byte[]> fichier(@PathVariable Long idDmc, @PathVariable Long idDemande) {
        EvaluationDemande d = service.fichierReponse(idDmc, idDemande);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, Telechargements.disposition(d.getReponseNom()))
                .contentType(MediaType.parseMediaType(d.getReponseFormat())).body(d.getReponseContenu());
    }

    @GetMapping("/journal")
    public List<EvaluationDto.Journal> journal(@PathVariable Long idDmc) {
        return service.journal(idDmc);
    }
}
