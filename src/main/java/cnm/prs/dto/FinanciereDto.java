package cnm.prs.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ⚠️ 2026-10-08 (lot 3 PI, tranche PI-d2a, §B5 ; V89) — l'évaluation financière des propositions de prestations intellectuelles et
 * leur classement selon la méthode de la fiche ({@code B02-MS-01}) : {@code codeMethode} ∈ {@code QUALITE_COUT}, {@code BUDGET},
 * {@code MOINDRE_COUT}, {@code QUALITE_TECHNIQUE}, {@code QUALIFICATION}.
 */
public record FinanciereDto(Long idDmc, String methode, String codeMethode, BigDecimal poidsTechnique, BigDecimal poidsFinancier,
        BigDecimal budget, List<Lot> lots) {

    public record Lot(Integer lot, Arret arret, Departage departage, List<Ligne> propositions) {
    }

    public record Arret(LocalDateTime le, String par, String nom, String observation, LocalDateTime rouvertLe, String motifReouverture) {
    }

    public record Departage(List<String> ordre, String motif, String par, String nom, LocalDateTime le) {
    }

    /**
     * Une proposition qualifiée : {@code statut} ∈ {@code NON_OUVERTE}, {@code A_EVALUER}, {@code EVALUEE}, {@code ECARTEE} (refus
     * d'une correction), {@code HORS_BUDGET} ; {@code montantCompare} = prix corrigé − dépenses remboursables (hors taxes) ;
     * {@code egalite} : à égalité avec une autre, non départagée.
     */
    public record Ligne(String idOffre, String idFinanciere, Integer numero, String nif, String raisonSociale, BigDecimal noteTechnique,
            Integer rangTechnique, boolean financiereOuverte, Saisie saisie, String statut, String motif, BigDecimal montantCompare,
            BigDecimal scoreFinancier, BigDecimal scoreCombine, Integer rang, boolean egalite) {
    }

    public record Saisie(BigDecimal prixLu, BigDecimal prixLuTtc, List<EvaluationDto.Correction> corrections, EvaluationDto.Refus refus,
            BigDecimal prixCorrige, BigDecimal remboursables, String motifRemboursables, String par, String nom, LocalDateTime le) {
    }

    /** {@code prixLu} seulement quand l'acte d'engagement n'en porte pas ; {@code remboursables} : 0 par défaut. */
    public record SaisieRequest(BigDecimal prixLu, List<EvaluationDto.Correction> corrections, BigDecimal remboursables,
            String motifRemboursables, EvaluationDto.Refus refusCandidat) {
    }

    public record DepartageRequest(List<String> ordre, String motif) {
    }
}
