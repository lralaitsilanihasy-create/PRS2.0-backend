package cnm.prs.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.CompteCandidat;
import cnm.prs.entity.Invitation;
import cnm.prs.repository.CompteCandidatRepository;
import cnm.prs.repository.InvitationRepository;

/**
 * ⚠️ V84 (lot 3 PI, tranche PI-a, §B1) — les invités de la consultation restreinte d'une procédure de prestations intellectuelles,
 * écrits à l'impression des lettres d'invitation : les candidats de la liste de l'AMI (leur compte), ou ceux saisis par la PRMP (leur
 * adresse électronique : le compte qui la porte leur est rattaché, même créé après l'invitation). Un candidat est invité s'il est l'un
 * d'eux ; la procédure n'est visible que des invités.
 */
@Service
@Transactional
public class InvitationsService {

    /** Un invité à écrire : sa source, son compte ou son adresse, son nom, son rang, sa lettre (PDF). */
    public record Invite(String source, String idCandidat, String email, String nom, int rang, Integer idDocument) {
    }

    private final InvitationRepository invitations;
    private final CompteCandidatRepository comptes;

    public InvitationsService(InvitationRepository invitations, CompteCandidatRepository comptes) {
        this.invitations = invitations;
        this.comptes = comptes;
    }

    /** Remplace les invités de la procédure (une nouvelle impression des lettres fait foi). */
    public void enregistrer(Long idDmc, List<Invite> invites, LocalDateTime le) {
        invitations.deleteByIdDmc(idDmc);
        invitations.flush();
        for (Invite i : invites) {
            invitations.save(new Invitation(null, idDmc, i.rang(), i.source(), i.idCandidat(), i.email() == null ? null : i.email().trim(), i.nom(),
                    i.idDocument(), le));
        }
    }

    /** L'invitation du candidat à cette procédure, s'il en a une (par son compte, ou par son adresse électronique). */
    @Transactional(readOnly = true)
    public java.util.Optional<Invitation> invitation(Long idDmc, String idCandidat) {
        if (idCandidat == null) {
            return java.util.Optional.empty();
        }
        String email = comptes.findById(idCandidat).map(CompteCandidat::getEmail).orElse(null);
        return invitations.findByIdDmcOrderByRangAsc(idDmc).stream()
                .filter(i -> idCandidat.equals(i.getIdCandidat()) || email != null && i.getEmail() != null && email.equalsIgnoreCase(i.getEmail()))
                .findFirst();
    }

    /** Les invitations du candidat, toutes procédures. */
    @Transactional(readOnly = true)
    public List<Invitation> invitationsDe(String idCandidat) {
        String email = comptes.findById(idCandidat).map(CompteCandidat::getEmail).orElse("");
        return invitations.findByIdCandidatOrEmailIgnoreCase(idCandidat, email).stream()
                .filter(Objects::nonNull).toList();
    }
}
