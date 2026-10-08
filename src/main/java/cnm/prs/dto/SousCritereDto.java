package cnm.prs.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * ⚠️ 2026-10-08 (lot 3 PI, tranche PI-a, Q3) — un sous-critère technique de la fiche : {@code critere} ∈ {@code B06-TP-02} …
 * {@code B06-TP-06}, son libellé, ses points ; {@code ordre} servi en lecture (la position dans la liste).
 */
public record SousCritereDto(Integer ordre, String critere, String libelle, BigDecimal points) {

    /** {@code PUT …/sous-criteres} : la liste entière, l'ordre est la position. */
    public record Remplacement(List<SousCritereDto> sousCriteres) {
    }
}
