package cnm.prs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de transfert pour {@link cnm.prs.entity.SousTypeDossier} — référentiel des sous-types de
 * dossier, rattachés à une famille ({@code idTypeDossier} = DDP / DMC / DDM).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SousTypeDossierDto {

    @NotBlank
    @Size(max = 20)
    private String idSousType;

    @Size(max = 150)
    private String libelleSousType;

    /** Famille de rattachement (FK {@code tr_type_dossier}). */
    @NotBlank
    @Size(max = 10)
    private String idTypeDossier;
}
