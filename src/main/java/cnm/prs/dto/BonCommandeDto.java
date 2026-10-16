package cnm.prs.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ⚠️ 2026-10-16 (demande front « marchés subséquents », lot MS-3 ; V126) — un bon de commande d'un contrat en vigueur : le bon
 * « réglementaire » du marché subséquent d'un contrat-cadre ({@code idRemise}), ou un bon du marché à commandes.
 *
 * @param etat     {@code EMIS} (notifié au titulaire) ou {@code ANNULE} (hors des cumuls)
 * @param accuseLe la première lecture du bon par le titulaire dans son espace : son accusé de réception
 */
public record BonCommandeDto(Long id, Long idContrat, Long idDmc, Integer lot, String typeMarche, Integer numero, Long idRemise,
        Integer numeroRemise, String idOffre, Integer numeroOffre, String titulaire, String etat, String objet, String lieuLivraison,
        String delaiLivraison, String observations, List<Ligne> lignes, BigDecimal montantHt, LocalDateTime emisLe, String emisPar,
        LocalDateTime accuseLe, LocalDateTime annuleLe, String annulePar, String motifAnnulation) {

    /** Un article : {@code prixDuBordereau} quand le prix unitaire est repris du bordereau scellé de l'offre (marché à commandes). */
    public record Ligne(Integer rang, Integer idArticle, String designation, String unite, BigDecimal quantite, BigDecimal prixUnitaire,
            BigDecimal montant, boolean prixDuBordereau) {
    }

    /** {@code POST /api/contrats-en-vigueur/{id}/bons-commande}. */
    public record BonRequest(Long idRemise, String objet, String lieuLivraison, String delaiLivraison, String observations,
            List<LigneRequest> lignes) {
    }

    /**
     * Un article demandé : {@code idArticle} (un article du besoin de la fiche : désignation, unité et, s'il est lisible, prix du
     * bordereau repris) ou {@code designation} libre ; {@code prixUnitaire} HT exigé quand le bordereau ne le donne pas.
     */
    public record LigneRequest(Integer idArticle, String designation, String unite, BigDecimal quantite, BigDecimal prixUnitaire) {
    }

    /** {@code POST /api/bons-commande/{id}/annulation}. */
    public record AnnulationRequest(String motif) {
    }

    /**
     * Les bons d'un contrat et sa consommation ({@code GET /api/contrats-en-vigueur/{id}/bons-commande}).
     *
     * @param cumulHt      les bons émis (non annulés) du contrat, toute sa durée
     * @param anneeDebut   marché à commandes : l'année contractuelle en cours (le minimum et le maximum sont annuels, {@code B05-TP-02/03})
     * @param cumulAnnee   marché à commandes : les bons émis de l'année contractuelle en cours
     * @param resteMaximum marché à commandes : ce qui reste à commander sous le maximum de l'année ; nul sans maximum
     * @param alertes      {@code MINIMUM_NON_ATTEINT} (marché à commandes, fin d'année contractuelle à 30 jours au plus, loi art. 30-II),
     *                     {@code MONTANT_ESTIME_DEPASSE} (contrat-cadre : le cumul dépasse la valeur maximale estimée, décret art. 8)
     */
    public record Consommation(Long idContrat, String typeMarche, BigDecimal cumulHt, LocalDate anneeDebut, LocalDate anneeFin,
            BigDecimal cumulAnnee, BigDecimal minimum, BigDecimal maximum, BigDecimal resteMaximum, BigDecimal montantEstime,
            List<Subsequent> subsequents, List<String> alertes, List<BonCommandeDto> bons) {
    }

    /** Contrat-cadre : un marché subséquent attribué, son montant et ce que ses bons en ont commandé. */
    public record Subsequent(Long idRemise, Integer numero, String objet, String idOffre, String titulaire, BigDecimal montantRetenu,
            BigDecimal commande, BigDecimal reste) {
    }
}
