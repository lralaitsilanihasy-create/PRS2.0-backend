package cnm.prs.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de transfert pour {@link cnm.prs.entity.TypePieceJointe} (référentiel des pièces par type de dossier).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TypePieceJointeDto {

    private Integer idTypePiece;

    @NotNull
    @Size(max = 200)
    private String libellePiece;

    /** Code stable facultatif (ex. {@code AGPM}) — repère la pièce à obligation conditionnelle. */
    @Size(max = 20)
    private String code;

    @NotNull
    private Boolean obligatoire;

    @Size(max = 10)
    private String idTypeDossier;

    private Integer ordre;

    // ⚠️ 2026-10-08 (manuel de contrôle, M2) — servis pour un sous-type (?sousType=) ou un dossier : la condition de la pièce.
    /** {@code FOURNITURES_SERVICES}, {@code TRAVAUX}, {@code PRESTATIONS_INTELLECTUELLES} ; nul : toutes. */
    private String categorie;
    /** {@code CONTRAT_CADRE}, {@code AUTRE} ; nul : toutes formes. */
    private String forme;
}
