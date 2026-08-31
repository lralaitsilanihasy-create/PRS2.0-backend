package cnm.prs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps de {@code POST /api/pv-examens/{id}/viser} — ⚠️ réforme « Visa unique » (arbitrage du pilote
 * du 2026-08-31).
 *
 * <p>Le visa <strong>fusionne</strong> l'ancienne clôture de navette ({@code accepter}) et la
 * signature du Président / Chef de commission : un seul geste, une seule transaction. Il pose l'avis
 * (éventuellement modifié), le Secrétaire de séance, le Membre co-signataire, et la part de
 * signature du rôle de l'acteur.</p>
 *
 * <p><strong>Pourquoi un record dédié plutôt qu'une extension de {@link PvActionRequest}</strong> :
 * ce dernier a tous ses champs optionnels — c'est le corps commun de {@code soumettre} /
 * {@code retourner} / {@code signer}. Le visa, lui, en EXIGE deux. Les porter ici en
 * {@code @NotBlank} donne un <strong>400</strong> par la validation, au lieu du 409 qu'aurait rendu
 * une garde en service.</p>
 *
 * @param imActeur    <strong>ignoré</strong> — l'acteur est l'utilisateur authentifié
 *                    ({@code CurrentUser.ref()}), jamais une valeur du corps. Conservé pour la
 *                    compatibilité ascendante du contrat, comme dans {@link PvActionRequest}.
 * @param commentaire commentaire libre du visa (facultatif), tracé sur la navette
 * @param idAvis      <strong>optionnel</strong> : absent → l'avis émis par le Membre à la soumission
 *                    est conservé ; fourni → il le remplace, après revalidation de la cohérence
 *                    (≥ 1 observation ⇒ {@code FAV} refusé). ⚠️ 409 si absent ET que le PV n'en porte
 *                    aucun (PV en navette au moment du déploiement, cf. §6 de la spec).
 * @param idSecretaireSeance Vérificateur désigné Secrétaire de séance — <strong>obligatoire</strong>.
 *                    Gardes §3.3 inchangées (titulaire de la localité du dossier, ou couvert par une
 *                    paire « → Vérificateur » active).
 * @param imMembreCoSignataire Membre appelé à co-signer — <strong>obligatoire</strong>. Gardes du
 *                    2026-08-28 inchangées : Membre titulaire de la localité, différent de l'acteur.
 */
public record PvVisaRequest(

        @Size(max = 7)
        String imActeur,

        String commentaire,

        @Size(max = 10)
        String idAvis,

        @NotBlank(message = "Le Secrétaire de séance est obligatoire pour viser.")
        @Size(max = 7)
        String idSecretaireSeance,

        @NotBlank(message = "Le Membre co-signataire est obligatoire pour viser.")
        @Size(max = 7)
        String imMembreCoSignataire) {
}
