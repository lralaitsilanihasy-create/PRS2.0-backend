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

    /** ⚠️ Tranche 1b (§B3.1) — les corrections arithmétiques proposées depuis le bordereau scellé ; 409 {@code OFFRE_ECARTEE}. */
    @GetMapping("/offres/{idOffre}/corrections-proposees")
    public List<EvaluationDto.CorrectionProposee> correctionsProposees(@PathVariable Long idDmc, @PathVariable String idOffre) {
        return service.correctionsProposees(idDmc, idOffre);
    }

    /** ⚠️ Tranche 1b (§B3.2) — corrections retenues, refus du candidat, rabais, préférence, critères ; le montant évalué est calculé. */
    @PutMapping("/offres/{idOffre}/montant")
    public EvaluationDto montant(@PathVariable Long idDmc, @PathVariable String idOffre, @RequestBody EvaluationDto.MontantRequest corps) {
        return service.montant(idDmc, idOffre, corps);
    }

    /** ⚠️ Tranche 1b (§B3.6, Q5) — le départage d'offres classées à égalité, avec un motif. */
    @PostMapping("/lots/{lot}/departage")
    public EvaluationDto departager(@PathVariable Long idDmc, @PathVariable Integer lot, @RequestBody EvaluationDto.DepartageRequest corps) {
        return service.departager(idDmc, lot, corps);
    }

    /** ⚠️ Tranche 1b (§B3) — le tableau d'évaluation du lot (modèle du guide, p. 9), par rang puis par numéro. */
    @GetMapping("/lots/{lot}/tableau")
    public List<EvaluationDto.LigneTableau> tableau(@PathVariable Long idDmc, @PathVariable Integer lot) {
        return service.tableau(idDmc, lot);
    }

    /** ⚠️ Tranche 1c (§B4) — les indicateurs de prix du lot : écarts à l'estimation et à la moyenne, jamais une décision. */
    @GetMapping("/lots/{lot}/indicateurs-prix")
    public EvaluationDto.IndicateursPrix indicateursPrix(@PathVariable Long idDmc, @PathVariable Integer lot) {
        return service.indicateursPrix(idDmc, lot);
    }

    /** ⚠️ Tranche 1c (§B4, art. 48) — 201 ; la PRMP demande au candidat de justifier son prix ; 409 {@code DEJA_DEMANDEE}. */
    @PostMapping("/offres/{idOffre}/justification")
    public ResponseEntity<EvaluationDto.Demande> demanderJustification(@PathVariable Long idDmc, @PathVariable String idOffre,
            @RequestBody EvaluationDto.JustificationRequest corps) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.demanderJustification(idDmc, idOffre, corps));
    }

    @GetMapping("/offres/{idOffre}/justification")
    public List<EvaluationDto.Demande> justifications(@PathVariable Long idDmc, @PathVariable String idOffre) {
        return service.justifications(idDmc, idOffre);
    }

    /** ⚠️ Tranche 1c (§B4) — non suspectée, suspectée, maintenue ou rejetée (jamais sans demande écrite). */
    @PutMapping("/offres/{idOffre}/anormale")
    public EvaluationDto anormale(@PathVariable Long idDmc, @PathVariable String idOffre, @RequestBody EvaluationDto.AnormaleRequest corps) {
        return service.anormale(idDmc, idOffre, corps);
    }

    /** ⚠️ Tranche 1c (§B5) — l'offre dont c'est le tour de post-qualification, et ses critères ; 409 {@code CLASSEMENT_NON_ARRETE}. */
    @GetMapping("/lots/{lot}/qualification")
    public EvaluationDto.Qualification qualification(@PathVariable Long idDmc, @PathVariable Integer lot) {
        return service.qualificationCourante(idDmc, lot);
    }

    @PutMapping("/offres/{idOffre}/qualification")
    public EvaluationDto qualifier(@PathVariable Long idDmc, @PathVariable String idOffre, @RequestBody EvaluationDto.QualificationRequest corps) {
        return service.qualifier(idDmc, idOffre, corps);
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
