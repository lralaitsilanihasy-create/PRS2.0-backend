package cnm.prs.dto;

import jakarta.validation.constraints.Size;

/**
 * Corps de requête commun aux actions de workflow du projet de PV
 * (soumettre / retourner / accepter / signer).
 *
 * <p>⚠️ Audit 2026-08-27 (lot B) — {@code imActeur} est <strong>ignoré par le serveur</strong> :
 * l'acteur d'une signature comme d'un mouvement de navette est l'utilisateur authentifié
 * ({@code CurrentUser.ref()}), jamais une valeur du corps de requête. Le champ reste accepté (le
 * front l'envoie encore, avec sa propre {@code ref}) mais n'est plus obligatoire — d'où le retrait
 * de {@code @NotBlank}, qui ferait échouer une requête pour un champ sans effet.</p>
 *
 * @param imActeur    <strong>ignoré</strong> — conservé pour compatibilité ascendante du contrat
 * @param commentaire commentaire ; obligatoire pour un retour de rectification (§3.2)
 * @param role        rôle du signataire (MEMBRE / PRESIDENT / CC) — uniquement pour « signer »
 * @param idAvis      ⚠️ règle ajoutée (2026-08-01) — avis global posé à la CLÔTURE DE NAVETTE
 *                    (« accepter », Président/CC) ; obligatoire pour accepter, ignoré ailleurs
 * @param idSecretaireSeance Vérificateur (localité du dossier) désigné Secrétaire de séance —
 *                    posé à la clôture de navette (« accepter »), ignoré ailleurs
 */
public record PvActionRequest(

        @Size(max = 7)
        String imActeur,

        String commentaire,

        @Size(max = 20)
        String role,

        @Size(max = 10)
        String idAvis,

        @Size(max = 7)
        String idSecretaireSeance) {
}
