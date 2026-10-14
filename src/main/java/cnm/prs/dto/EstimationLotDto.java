package cnm.prs.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * ⚠️ V116 (2026-10-14, demande front « q11-prix-estimatifs », §B2) — l'estimation d'un lot de la fiche DAO face au plan
 * ({@code FicheMarcheDto.estimations}) : {@code lot} est le rang du lot ({@code null} sur une ligne non allotie) ; {@code montantPlan}
 * le montant HT du lot au plan (nul s'il n'y est pas renseigné) ; {@code totalEstime} la somme quantité × prix unitaire estimé des
 * articles qui ont un prix ; {@code ecart} = total estimé − montant du plan, nul si l'un manque ; {@code articlesSansPrix} les
 * articles du lot qui n'ont pas encore de prix (« n° 001 — Béton », « article 2 — Ordinateur »).
 */
public record EstimationLotDto(Integer lot, BigDecimal montantPlan, BigDecimal totalEstime, BigDecimal ecart, int nbArticles,
        List<String> articlesSansPrix) {
}
