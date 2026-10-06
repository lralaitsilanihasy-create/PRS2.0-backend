package cnm.prs.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 1c, §B8) — une procédure ouverte en ligne, telle que le public
 * la lit ({@code GET /api/procedures-en-ligne}). Les champs viennent de la fiche validée (rubrique {@code B04-SE}, échéance) :
 * <strong>aucun paramètre interne</strong> (V50, ADR-0010) n'y figure. Dates en ISO local (heure de référence à part).
 * {@code etat} : {@code A_VENIR} (avant l'ouverture des dépôts), {@code OUVERTE}, {@code CLOSE} (date limite passée).
 */
public record ProcedureEnLigneDto(Long idDmc, String reference, String objet, String autoriteContractante, String categorie,
        List<Lot> lots, String datePublication, String dateOuvertureDepots, String dateLimite, String heureReference,
        String signatureExigee, List<String> formatsAcceptes, Integer tailleMaxFichierMo, Integer tailleMaxOffreMo,
        String assistance, String etat,
        /** ⚠️ 2026-10-04 (lot 3, §B1) — {@code B04-SE-10} : remplacer et retirer son offre avant la date limite. */
        boolean remplacementAutorise,
        /** ⚠️ 2026-10-04 (lot 3, §B1) — la condition 2 du dépôt : {@code etat = OUVERTE}. */
        boolean depotsOuverts,
        /**
         * ⚠️ 2026-10-06 (retrait après paiement, §B1) — les frais de dossier par lot ({@code B04-DS-05#n}, à défaut {@code B04-DS-05},
         * contrat-cadre de travaux {@code B04-DK-04}), {@code null} pour un dossier gratuit (retrait libre).
         */
        List<Frais> fraisDossier,
        /** ⚠️ §B1 — le compte de l'ARMP à créditer ({@code PARAM.compte-dao}), {@code null} pour un dossier gratuit. */
        CompteDao compteDao,
        /**
         * ⚠️ §B4, H2 — le retrait exige un reçu validé : un dossier payant dont l'avis est imprimé pour la première fois à partir
         * de la livraison ({@code RETRAIT_PAYANT_DEPUIS}) ; faux pour un dossier gratuit ou une procédure lancée avant.
         */
        boolean retraitPayant) {

    /** Les frais d'un lot ({@code lot = null} : marché non alloti). */
    public record Frais(Integer lot, java.math.BigDecimal montant) {
    }

    public record CompteDao(String banque, String titulaire, String numeroCompte) {
    }

    public record Lot(int numero, String intitule) {
    }

    /** Un document du DAO à retirer ({@code GET …/documents}) : {@code code} sert à le télécharger. */
    public record Document(String code, String intitule, Integer version, long taille) {
    }

    /** Une ligne du registre des retraits ({@code GET /api/fiches-marche/{idDmc}/retraits}, PRMP de la fiche). */
    public record Retrait(LocalDateTime date, String compte, String entreprise, String nif, String document, Integer version,
            /** ⚠️ 2026-10-06 (§B6) — le reçu qui a ouvert le retrait ({@code null} : retrait libre). */
            RecuRetrait recu) {
    }

    public record RecuRetrait(String etat, String referencePaiement) {
    }
}
