package cnm.prs.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ⚠️ V61 (demande front du 2026-10-03, pièces de l'offre des travaux, §B1.2) — une pièce exigée d'une fiche DAO de
 * travaux ({@code GET/PUT /api/fiches-marche/{idDmc}/pieces}). {@code rubrique} : {@code ADMINISTRATIVE} (2° de la clause
 * 6.2 du DPAO-T) ou {@code OFFRE} (1°). {@code ordre} : servi ; à l'écriture, la position dans la liste fait foi.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PieceExigeeDto {

    private Integer idPiece;
    private Integer ordre;
    private String rubrique;
    private String numero;
    private String libelle;
    private String forme;
    private Integer ancienneteMaxMois;
    private Boolean parLot;
    private String modele;

    /** Corps de {@code PUT /api/fiches-marche/{idDmc}/pieces}. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Remplacement {
        private List<PieceExigeeDto> pieces;
    }
}
