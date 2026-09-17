package cnm.prs.dto;

import java.time.LocalDateTime;

/**
 * ⚠️ Lot 6 (2026-09-17, demande front §B4) — une ligne du <strong>journal des connexions</strong>
 * ({@code GET /api/sessions}). Remplace {@code SessionUtilisateurDto}, qui servait un CRUD complet sur
 * la même table : un journal de preuve modifiable est pire qu'absent.
 *
 * <p><strong>L'identifiant de session n'est volontairement pas exposé</strong> : c'est l'empreinte du
 * jeton émis (cf. {@code JournalConnexionService}), elle n'a aucun usage à l'écran, et une empreinte de
 * jeton n'a pas à circuler pour rien.</p>
 *
 * @param acteur          référence de la personne connectée — {@code IM_CONTROLEUR}, {@code ID_PRMP} ou
 *                        {@code ID_UGPM} ; {@code null} quand la tentative a échoué sur un login
 *                        inconnu, il n'y a alors personne à désigner
 * @param login           identifiant <strong>tenté</strong>, renseigné dans tous les cas
 * @param dateConnexion   horodatage de la tentative
 * @param dateDeconnexion fermeture par {@code POST /api/auth/logout} ; {@code null} si la session est
 *                        encore ouverte — ou si l'utilisateur a simplement fermé son onglet, ce que
 *                        rien ne permet de distinguer
 * @param dureeSecondes   durée de la session ; {@code null} tant qu'elle n'est pas fermée, et pour un
 *                        échec, qui n'a pas de durée
 * @param ipAdresse       adresse de l'appelant
 * @param userAgent       le « poste » de la maquette : l'en-tête {@code User-Agent} du navigateur
 * @param succes          {@code true} si les identifiants ont été acceptés
 */
public record SessionDto(
        String acteur,
        String login,
        LocalDateTime dateConnexion,
        LocalDateTime dateDeconnexion,
        Long dureeSecondes,
        String ipAdresse,
        String userAgent,
        Boolean succes) {
}
