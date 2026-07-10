package cnm.prs.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de transfert pour {@link cnm.prs.entity.ModePassation}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModePassationDto {

    private Integer idMode;

    @Size(max = 100)
    private String libelle;

    @Size(max = 500)
    private String description;

    private Boolean publiciteRequise;

    private Integer delaiMinJours;

    @Size(max = 200)
    private String baseLegale;

    /** Mapping vers le type de DMC (écran admin des modes de passation). */
    private Long idTypeDmc;
}
