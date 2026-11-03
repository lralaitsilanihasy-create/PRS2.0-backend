package cnm.prs.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.OuvertureAnticipeeDto;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.security.CurrentUser;
import cnm.prs.service.OuvertureAnticipeeService;

/**
 * ⚠️ V160 (2026-11-03, demande front « appel-offres-restreint », §B7) — l'ouverture anticipée des plis d'un appel d'offres restreint
 * en ligne : la PRMP la demande et fixe la nouvelle date (sous la fiche), chaque invité y consent ou la refuse par écrit (sous ses
 * invitations). Voir {@link OuvertureAnticipeeService}.
 */
@RestController
public class OuvertureAnticipeeController {

    private final OuvertureAnticipeeService service;

    public OuvertureAnticipeeController(OuvertureAnticipeeService service) {
        this.service = service;
    }

    /** La demande (lecteurs de la fiche) ; vide sans demande. */
    @GetMapping("/api/fiches-marche/{idDmc}/ouverture-anticipee")
    public OuvertureAnticipeeDto lire(@PathVariable Long idDmc) {
        return service.lire(idDmc);
    }

    /** Demander les consentements (PRMP) : 201 ; 409 {@code HORS_RESTREINT}, {@code DEPOTS_CLOS}, {@code DEJA_DEMANDEE}, {@code DEPOTS_INCOMPLETS}. */
    @PostMapping("/api/fiches-marche/{idDmc}/ouverture-anticipee")
    public ResponseEntity<OuvertureAnticipeeDto> demander(@PathVariable Long idDmc) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.demander(idDmc));
    }

    /** Fixer la nouvelle heure d'ouverture (PRMP), corps {@code {"nouvelleOuverture": "AAAA-MM-JJTHH:MM"}}. */
    @PutMapping("/api/fiches-marche/{idDmc}/ouverture-anticipee/date")
    public OuvertureAnticipeeDto fixer(@PathVariable Long idDmc, @RequestBody(required = false) OuvertureAnticipeeDto.Fixation corps) {
        return service.fixer(idDmc, corps);
    }

    /** La demande vue par l'invité (son seul consentement) ; vide sans demande. */
    @GetMapping("/api/candidat/invitations/{idDmc}/ouverture-anticipee")
    @PreAuthorize("hasRole('CANDIDAT')")
    public OuvertureAnticipeeDto lireCandidat(@PathVariable Long idDmc) {
        return service.lirePourCandidat(moi(), idDmc);
    }

    /** Consentir ou refuser par écrit, corps {@code {"accord": true|false}}. */
    @PostMapping("/api/candidat/invitations/{idDmc}/ouverture-anticipee/consentement")
    @PreAuthorize("hasRole('CANDIDAT')")
    public OuvertureAnticipeeDto repondre(@PathVariable Long idDmc, @RequestBody(required = false) OuvertureAnticipeeDto.Reponse corps) {
        return service.repondre(moi(), idDmc, corps);
    }

    private static String moi() {
        return CurrentUser.ref().orElseThrow(() -> new ResourceNotFoundException("Compte candidat introuvable."));
    }
}
