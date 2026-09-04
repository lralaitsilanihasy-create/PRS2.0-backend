package cnm.prs.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Corps de {@code POST /api/pv-examens/{id}/viser} — ⚠️ réforme « Visa unique » (arbitrage du pilote
 * du 2026-08-31).
 *
 * <p>Le visa <strong>fusionne</strong> l'ancienne clôture de navette ({@code accepter}) et la
 * signature du Président / Chef de commission : un seul geste, une seule transaction. Il pose l'avis
 * (éventuellement modifié), le Membre co-signataire, et la part de signature du rôle de l'acteur.</p>
 *
 * <p>⚠️ <strong>Le Secrétaire de séance a disparu du visa</strong> (règle du pilote, 2026-09-02) —
 * voir {@link #idSecretaireSeance()}.</p>
 *
 * <p><strong>Pourquoi un record dédié plutôt qu'une extension de {@link PvActionRequest}</strong> :
 * ce dernier a tous ses champs optionnels — c'est le corps commun de {@code soumettre} /
 * {@code retourner} / {@code signer}. Le visa, lui, en EXIGE un (le co-signataire). Le porter ici en
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
 * @param idSecretaireSeance ⚠️ <strong>Retiré du cycle du PV</strong> (règle du pilote, 2026-09-02) :
 *                    plus exigé, plus validé, plus écrit. Un client non à jour qui l'envoie encore
 *                    n'est pas refusé — la valeur est <strong>ignorée</strong>, même esprit que la
 *                    note d'intérim envoyée par un dispatcheur. Le champ reste dans le contrat pour
 *                    ne pas casser ces clients ; il disparaîtra quand ils auront tous suivi.
 * @param imMembreCoSignataire Membre appelé à co-signer — ⚠️ RÉTRO-COMPATIBILITÉ depuis le
 *                    2026-09-04 : remplacé par {@link #coSignataires()}, il reste accepté seul et
 *                    équivaut alors à une liste d'un élément. Fourni EN MÊME TEMPS que la liste, il
 *                    est ignoré — deux sources pour une même désignation ne peuvent pas se
 *                    contredire silencieusement, c'est la liste qui fait foi.
 * @param coSignataires ⚠️ <strong>Co-signature élargie</strong> (spec pilote du 2026-09-04) — de UN à
 *                    DEUX matricules, le Président signant toujours par ailleurs. Combinaisons
 *                    admises sur une navette à deux niveaux : le CC du circuit, le Membre
 *                    examinateur, ou un autre Membre de la localité centrale. Au plus un CC et au
 *                    plus un Membre : le PV n'a qu'une ligne de signature par rôle, et deux Membres
 *                    n'auraient nulle part où signer. Sur une navette simple, le contrat ne bouge
 *                    pas : un seul co-signataire, Membre de la localité.
 */
public record PvVisaRequest(

        @Size(max = 7)
        String imActeur,

        String commentaire,

        @Size(max = 10)
        String idAvis,

        @Size(max = 7)
        String idSecretaireSeance,

        @Size(max = 7)
        String imMembreCoSignataire,

        /**
         * ⚠️ Validé EN SERVICE, pas ici. La @NotBlank qui portait sur {@code imMembreCoSignataire} a
         * été retirée : elle rendait impossible le seul envoi de {@code coSignataires}, alors que
         * l'un OU l'autre suffit désormais. La règle « au moins un désigné » ne s'exprime plus sur un
         * champ isolé — elle porte sur le couple. Le 400 est rendu par le service, avec un message
         * qui nomme les combinaisons admises plutôt qu'un « ne doit pas être vide » aveugle.
         */
        List<@Size(max = 7) String> coSignataires) {
}
