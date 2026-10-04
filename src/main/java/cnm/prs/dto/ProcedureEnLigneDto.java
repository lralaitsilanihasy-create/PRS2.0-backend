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
        String assistance, String etat) {

    public record Lot(int numero, String intitule) {
    }

    /** Un document du DAO à retirer ({@code GET …/documents}) : {@code code} sert à le télécharger. */
    public record Document(String code, String intitule, Integer version, long taille) {
    }

    /** Une ligne du registre des retraits ({@code GET /api/fiches-marche/{idDmc}/retraits}, PRMP de la fiche). */
    public record Retrait(LocalDateTime date, String compte, String entreprise, String nif, String document, Integer version) {
    }
}
