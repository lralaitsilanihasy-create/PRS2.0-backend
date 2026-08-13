package cnm.prs.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de transfert pour {@link cnm.prs.entity.ExamenPiece} — examen d'une pièce jointe (⚠️ règle ajoutée).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExamenPieceDto {

    private Integer idExamenPiece;

    @NotNull
    private Integer idExamen;

    @NotNull
    private Integer idPiece;

    @NotNull
    private Boolean conforme;

    @Size(max = 500)
    private String observation;
}
