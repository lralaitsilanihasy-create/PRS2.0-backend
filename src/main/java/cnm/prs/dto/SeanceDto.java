package cnm.prs.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 4 ; ADR-0013 §1, §2, §5) — la <strong>séance d'ouverture des plis</strong>
 * ({@code GET /api/fiches-marche/{idDmc}/seance}). {@code etat} ∈ {@code A_VENIR} · {@code OUVERTE} · {@code DECHIFFREE} ·
 * ⚠️ V70 {@code PV_A_SIGNER} · {@code ILLISIBLE} · {@code CLOSE} ; {@code heureOuverture} = {@code B04-OP-02} + {@code B04-OP-03} ; {@code ouverteDans} : les secondes
 * avant l'heure d'ouverture, {@code null} une fois l'heure passée.
 */
public record SeanceDto(Long idDmc, String etat, LocalDateTime heureOuverture, LocalDateTime ouverteLe, Long ouverteDans, Integer quorum,
        List<Membre> membres, List<Autre> autres, boolean secoursEmploye, List<OffreSeance> offres, LocalDateTime dechiffreeLe, Pv pv,
        SecoursDemande secoursDemande, String secoursGenerePar) {

    /**
     * ⚠️ 2026-10-05 (dépositaire, §B3) — la part de secours demandée au dépositaire par le responsable ({@code POST …/secours}),
     * {@code null} sinon. {@code secoursGenerePar} : qui a généré la clé de secours active — {@code DEPOSITAIRE} (il apporte sa part
     * depuis son espace, une fois demandée) ou {@code RESPONSABLE} (ancien geste : le responsable l'apporte, motif au corps).
     */
    public record SecoursDemande(String motif, LocalDateTime date) {
    }

    /** {@code POST …/secours} : le motif de l'emploi de la part de secours, porté au PV. */
    public record DemandeSecours(String motif) {
    }

    /** Un membre de la CAO : {@code partsApportees} vrai dès qu'il a apporté toutes ses parts (en mémoire de la séance). */
    public record Membre(String im, String nom, boolean president, boolean present, boolean partsApportees) {
    }

    public record Autre(String nom, String qualite) {
    }

    /** Une offre de la séance : {@code partsRecues} = le nombre de détenteurs qui ont apporté leur part pour elle. */
    public record OffreSeance(Integer numero, Integer lot, String etat, int partsRecues) {
    }

    /**
     * Le PV : ⚠️ 2026-10-04 (arbitrages du pilote, §B2) — {@code signe} quand tous les membres présents ont signé (ou que leur
     * empêchement est constaté) ; {@code signatures} posées, {@code signaturesAttendues} restantes. Publié seulement une fois signé.
     */
    public record Pv(boolean produit, boolean publie, boolean signe, List<Signature> signatures, List<Attendue> signaturesAttendues) {
    }

    /** Une signature électronique simple, ou un empêchement constaté ({@code empechement}, {@code motif}, {@code constatePar}). */
    public record Signature(String im, String nom, boolean president, LocalDateTime date, boolean empechement, String motif,
            String constatePar) {
    }

    public record Attendue(String im, String nom) {
    }

    /** {@code POST …/pv/empechement} (le président) : le membre présent empêché de signer, et le motif porté au PV. */
    public record Empechement(String im, String motif) {
    }

    /** {@code PUT …/presences} : les membres présents (identifiants {@code K…}) et les autres présents. */
    public record Presences(List<String> presents, List<Autre> autres) {
    }

    /**
     * {@code GET …/mes-parts} : pour chaque offre déposée, la part chiffrée pour la clé de l'appelant ; {@code enveloppe} est servie
     * quand l'offre a été scellée pour une clé depuis remplacée (archivée, S4) — à déverrouiller avec la phrase de l'époque.
     */
    public record PartChiffree(String idOffre, String empreinteCle, String part, CeremonieDto.Enveloppe enveloppe) {
    }

    /** {@code POST …/parts} : toutes ses parts claires (33 octets, base64), en une fois ; {@code motif} obligatoire pour la part de secours. */
    public record Apport(List<PartClaire> parts, String motif) {
    }

    public record PartClaire(String idOffre, String partClaire) {
    }

    /** {@code POST …/pv} : les observations de la séance. */
    public record Observations(String observations) {
    }

    /** {@code POST …/constater-illisible} : S5. */
    public record Constat(String motif) {
    }

    /** {@code GET …/lecture} : les offres ouvertes par rang d'arrivée, puis celles qui ne s'ouvrent pas. */
    public record Lecture(List<OffreLue> offres, List<NonOuverte> nonOuvertes) {
    }

    /**
     * Une offre lue : {@code integrite} ∈ {@code INTACTE} · {@code ALTEREE} · {@code LECTURE_IMPOSSIBLE} ; {@code groupement},
     * {@code acteEngagement} et {@code garantie} sont repris du manifeste tels quels. ⚠️ 2026-10-05 (lot 5, §B3) — {@code formulaires} :
     * l'offre porte des formulaires (manifeste format 3) ; {@code totaux} : recalculés à l'ouverture depuis le bordereau et les
     * quantités de la fiche ({@code ht}, {@code tva}, {@code ttc}, et {@code htMin} / {@code ttcMin} à commande, {@code parSerie}
     * aux travaux), {@code null} sans formulaires ; leurs alertes rejoignent {@code alertes}. ⚠️ §B5 — {@code partiesFormulaires} :
     * les documents remplis que l'offre porte, parmi {@code BORDEREAU} · {@code CONFORMITE} · {@code CAPACITES} (ceux dont le PDF
     * existe), {@code null} sans formulaires.
     */
    public record OffreLue(Integer numero, String idOffre, Integer lot, String etat, String integrite, String motif, EntrepriseLue entreprise,
            Object groupement, Map<String, Object> acteEngagement, Garantie garantie, List<PieceLue> pieces, List<String> piecesManquantes,
            List<Alerte> alertes, boolean formulaires, Map<String, Object> totaux, List<String> partiesFormulaires) {
    }

    public record EntrepriseLue(String nif, String raisonSociale, EntrepriseCandidatDto.Verification verification,
            EntrepriseCandidatDto.Exclusion exclusion) {
    }

    /**
     * La garantie lue : ⚠️ 2026-10-04 (arbitrages du pilote, §B3) — {@code montant}, {@code monnaie}, {@code emetteur} du manifeste
     * (format 2) ; {@code null} pour un manifeste qui ne les porte pas (format 1), sans rendre l'offre illisible.
     */
    public record Garantie(String codeVerification, boolean presente, java.math.BigDecimal montant, String monnaie, String emetteur) {
    }

    public record PieceLue(String code, String libelle, boolean presente, String nomFichier, boolean empreinteConforme) {
    }

    /** {@code type} ∈ {@code RAPPROCHEMENT} · {@code EXCLUSION} ; une alerte, jamais un refus. */
    public record Alerte(String type, String message) {
    }

    /**
     * ⚠️ 2026-10-05 (lot 5, §B3.4) — {@code GET …/seance/offres/{idOffre}/formulaires} : la partie {@code formulaires} du manifeste telle
     * que scellée, et le besoin du lot (libellés, quantités) pour que l'écran n'ait pas à faire un second appel.
     */
    public record Formulaires(String idOffre, Integer numero, Integer lot, Object formulaires, BesoinEnLigneDto.Lot besoin) {
    }

    public record NonOuverte(Integer numero, String entreprise, String etat, String motif) {
    }
}
