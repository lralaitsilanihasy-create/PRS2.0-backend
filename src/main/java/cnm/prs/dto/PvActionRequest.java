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
 * @param idSecretaireSeance ⚠️ <strong>MORT depuis le 2026-09-02</strong> — le Secrétaire de séance a
 *                    été retiré du cycle du PV par le pilote. Ce champ n'était déjà plus lu que par
 *                    l'ancien « accepter », retiré en 410 ; il est conservé pour ne pas casser le
 *                    contrat d'un client non à jour, et <strong>ignoré</strong> en toute circonstance.
 * @param imMembreCoSignataire ⚠️ Co-signature (2026-08-28) — Membre désigné par le Président ou le
 *                    Chef de commission pour co-signer le PV. <strong>Obligatoire</strong> quand
 *                    ils signent (« signer », rôle PRESIDENT ou CC), ignoré ailleurs — même
 *                    traitement que l’ancien Secrétaire de séance. Le désigné doit
 *                    être un Membre de la localité du dossier et différent du signataire :
 *                    l'auto-co-signature n'est pas autorisée.
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
        String idSecretaireSeance,

        @Size(max = 7)
        String imMembreCoSignataire) {
}
