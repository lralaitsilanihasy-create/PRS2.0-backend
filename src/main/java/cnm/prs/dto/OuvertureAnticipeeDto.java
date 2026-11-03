package cnm.prs.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ⚠️ V160 (demande front « appel-offres-restreint », §B7) — la demande d'ouverture anticipée des plis.
 *
 * @param etat               {@code DEMANDEE}, {@code ACCORDEE}, {@code FIXEE} ou {@code REFUSEE}
 * @param ouvertureInitiale  l'heure d'ouverture de la fiche, au moment de la demande
 * @param nouvelleOuverture  l'heure fixée par la PRMP ({@code FIXEE}), sinon nulle
 * @param consentements      la réponse de chaque invité qui a déposé ({@code accord} nul tant qu'il n'a pas répondu)
 */
public record OuvertureAnticipeeDto(Long idDmc, String etat, LocalDateTime ouvertureInitiale, LocalDateTime nouvelleOuverture,
        LocalDateTime demandeeLe, String demandeePar, LocalDateTime fixeeLe, String fixeePar, List<Consentement> consentements) {

    public record Consentement(String idCandidat, String nom, Boolean accord, LocalDateTime reponduLe) {
    }

    /** Corps de {@code POST …/consentement} : {@code accord} vrai (consentement écrit) ou faux (refus). */
    public record Reponse(Boolean accord) {
    }

    /** Corps de {@code PUT …/date} : la nouvelle heure d'ouverture, ISO {@code AAAA-MM-JJTHH:MM}. */
    public record Fixation(String nouvelleOuverture) {
    }
}
