package cnm.prs.dto;

import java.util.List;

/**
 * ⚠️ 2026-10-01 (lot AV-4.1, demande front « lettres d'invitation », §B3) — corps de
 * {@code POST /api/fiches-marche/{idDmc}/lettres-invitation} : ce qui est saisi à l'impression, jamais écrit dans la
 * fiche. Tout est obligatoire ; la date au format ISO {@code AAAA-MM-JJ} ; au moins un candidat (décision Q4 : pas de
 * nombre imposé).
 *
 * @param dateEnvoi  date d'envoi des lettres
 * @param lieu       lieu d'envoi
 * @param candidats  la liste restreinte, dans l'ordre : une lettre par candidat
 */
public record LettreInvitationRequest(String dateEnvoi, String lieu, List<Candidat> candidats) {

    /** Un candidat de la liste restreinte : son nom, son adresse (plusieurs lignes possibles). */
    public record Candidat(String nom, String adresse) {
    }
}
