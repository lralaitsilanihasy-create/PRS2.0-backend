package cnm.prs.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ⚠️ 2026-10-09 (manuel de contrôle a priori, tranche M5a, §B1 et §B5 ; V98) — les actes de gestion contractuelle d'un marché.
 */
public final class ActeGestionDto {

    private ActeGestionDto() {
    }

    /**
     * Dépôt ou modification d'un acte. {@code sousType} : {@code AVN}, {@code DR}, {@code INDEMN}, {@code PENAL}, {@code SURSIS}
     * (ignoré à la modification). {@code montantHt} : l'avenant, hausse positive, baisse négative (ignoré hors avenant). Les faits du
     * marché ne se déclarent que si le serveur ne les connaît pas (montant initial, catégorie) ou pour les garde-fous (réceptions, solde).
     */
    public record Demande(String sousType, BigDecimal montantHt, BigDecimal montantInitialHt, String categorie,
            LocalDate dateReceptionProvisoire, LocalDate dateReceptionDefinitive, LocalDate dateSolde) {
    }

    /** Un acte : son dossier DGC (statut, référence, dernier avis signé) et ce qu'il déclare. */
    public record Acte(Integer idActe, Integer idDossier, Integer idDossierMarche, String sousType, Integer rang, BigDecimal montantHt,
            BigDecimal montantInitialHt, String categorie, LocalDate dateReceptionProvisoire, LocalDate dateReceptionDefinitive,
            LocalDate dateSolde, String statutDossier, String refeDossier, String avis, boolean compteDansLeCumul,
            LocalDateTime creeLe) {
    }

    /**
     * Le marché et ses actes : faits retenus (montant initial HT et sa source {@code ATTRIBUTION} | {@code DECLARE}, catégorie et sa
     * source {@code FICHE} | {@code DECLARE}, réceptions et solde déclarés), cumul des avenants comptés et plafond (tiers du montant
     * initial), avenant suivant.
     */
    public record Marche(Integer idDossierMarche, String sousTypeMarche, String refeDossier, String avisMarche, BigDecimal montantInitialHt,
            String sourceMontantInitial, String categorie, String sourceCategorie, LocalDate dateReceptionProvisoire,
            LocalDate dateReceptionDefinitive, LocalDate dateSolde, BigDecimal cumulAvenantsHt, BigDecimal plafondAvenantsHt,
            int rangAvenantSuivant, List<Acte> actes) {
    }
}
