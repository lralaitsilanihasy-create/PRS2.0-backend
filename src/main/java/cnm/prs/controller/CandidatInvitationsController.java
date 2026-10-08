package cnm.prs.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.InvitationDto;
import cnm.prs.entity.DocumentFicheMarche;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.security.CurrentUser;
import cnm.prs.service.ProceduresEnLigneService;

/**
 * ⚠️ 2026-10-08 (lot 3 PI, tranche PI-a, §B1 ; V84) — les consultations restreintes où le candidat est invité (par la liste de l'AMI,
 * ou par l'adresse électronique saisie par la PRMP), et sa lettre d'invitation. La procédure se lit ensuite par
 * {@code GET /api/procedures-en-ligne/{idDmc}}, visible de ses seuls invités.
 */
@RestController
@RequestMapping("/api/candidat/invitations")
@PreAuthorize("hasRole('CANDIDAT')")
public class CandidatInvitationsController {

    private final ProceduresEnLigneService service;

    public CandidatInvitationsController(ProceduresEnLigneService service) {
        this.service = service;
    }

    @GetMapping
    public List<InvitationDto> lister() {
        return service.invitationsDe(moi());
    }

    /** La lettre d'invitation (PDF) ; 404 sans invitation ou sans lettre. */
    @GetMapping("/{idDmc}/lettre")
    public ResponseEntity<byte[]> lettre(@PathVariable Long idDmc) {
        DocumentFicheMarche d = service.lettreInvitation(moi(), idDmc);
        if (d == null) {
            throw new ResourceNotFoundException("La lettre d'invitation n'est pas disponible.");
        }
        return Telechargements.fichier(d.getNomFichier(), d.getExtension(), d.getContenu());
    }

    private static String moi() {
        return CurrentUser.ref().orElseThrow(() -> new ResourceNotFoundException("Compte candidat introuvable."));
    }
}
