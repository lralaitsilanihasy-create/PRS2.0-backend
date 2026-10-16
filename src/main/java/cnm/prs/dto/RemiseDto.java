package cnm.prs.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ⚠️ 2026-10-16 (demande front « marchés subséquents », lot MS-2 ; V125) — une remise en concurrence des titulaires d'un contrat-cadre en
 * vigueur, ou la complétude de l'offre du titulaire unique ({@code mode = COMPLETUDE}).
 *
 * @param etat           {@code LANCEE} → {@code EVALUEE} (rapport simplifié de la CAO) → {@code ATTRIBUEE} ou {@code INFRUCTUEUSE}
 * @param echeancePassee les réponses ne s'ouvrent qu'une fois l'échéance passée : avant, leurs montants et fichiers sont nuls
 * @param consultes      tous les titulaires du contrat (art. 21), avec le constat de l'absence de réponse (art. 22)
 * @param alertes        {@code EXCLUSION_POSSIBLE} (un titulaire sans réponse à trois remises consécutives, DPAC art. 9),
 *                       {@code DELIEMENT_POSSIBLE} (deux remises consécutives dont la meilleure offre dépasse de plus de 30 % le prix moyen
 *                       du marché : l'acheteur est délié de l'exclusivité pour cet achat, décret art. 23), {@code PLAFOND_DEPASSE} (une
 *                       complétude au-delà du plafond d'augmentation du contrat, {@code B05-PM-04})
 */
public record RemiseDto(Long id, Long idContrat, Long idDmc, Integer lot, Integer numero, String mode, String etat, String objet,
        String specifications, String criteres, String lieuLivraison, String delaiExecution, Integer validiteJours, String visite,
        LocalDateTime echeance, boolean echeancePassee, LocalDateTime lanceeLe, String lanceePar, boolean lettreDisponible,
        List<Consulte> consultes, List<Reponse> reponses, BigDecimal prixMoyen, String idOffreProposee, String observations,
        LocalDateTime evalueeLe, String evalueePar, boolean rapportDisponible, String idOffreRetenue, BigDecimal montantRetenu,
        String motifDecision, LocalDateTime decideeLe, String decideePar, List<String> alertes) {

    /** Un titulaire consulté : {@code absencesConsecutives} compte cette remise et les précédentes du contrat restées sans sa réponse. */
    public record Consulte(String idOffre, Integer numero, String candidat, LocalDateTime notifieLe, boolean reponseRecue, boolean sansReponse,
            int absencesConsecutives) {
    }

    /**
     * Une réponse. Avant l'échéance : {@code montantHt}, {@code montantTtc}, {@code delai} et {@code fichier} nuls (réponses fermées).
     * {@code ecartPrixMoyen} en pour cent (nul sans prix moyen) ; {@code rang} parmi les réponses conformes, par montant hors taxes.
     */
    public record Reponse(Long id, String idOffre, Integer numero, String candidat, String nature, String voie, BigDecimal montantHt,
            BigDecimal montantTtc, String delai, LocalDateTime recueLe, String fichier, Boolean conforme, String motif,
            BigDecimal ecartPrixMoyen, Integer rang, boolean plafondDepasse) {
    }

    /** {@code POST /api/contrats-en-vigueur/{id}/remises}. */
    public record RemiseRequest(String objet, String specifications, String criteres, String lieuLivraison, String delaiExecution,
            Integer validiteJours, String visite, LocalDateTime echeance) {
    }

    /** {@code POST /api/remises/{id}/evaluation} : le prix moyen du marché, une décision par réponse, la proposition (facultative). */
    public record EvaluationRequest(BigDecimal prixMoyen, List<DecisionReponse> decisions, String idOffreProposee, String observations) {
    }

    public record DecisionReponse(String idOffre, Boolean conforme, String motif) {
    }

    /** {@code POST /api/remises/{id}/attribution} et {@code …/infructueuse}. */
    public record DecisionRequest(String motif) {
    }

    /**
     * La remise vue par un titulaire consulté ({@code GET /api/candidat/remises}) : l'invitation, sa propre réponse, et le résultat
     * ({@code EN_COURS}, {@code RETENU}, {@code NON_RETENU}, {@code INFRUCTUEUSE}) avec son motif.
     */
    public record PourTitulaire(Long id, Integer numero, String idOffre, String objet, String objetContrat,
            LocalDateTime echeance, boolean echeancePassee, String delaiExecution, String lieuLivraison, Reponse maReponse, String resultat,
            String motif, LocalDate dateContrat) {
    }
}
