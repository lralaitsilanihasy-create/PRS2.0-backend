package cnm.prs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de transfert pour {@link cnm.prs.entity.TypeDmc} (référentiel des types de DMC).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TypeDmcDto {

    private Long idTypeDmc;

    @NotBlank
    @Size(max = 10)
    private String code;

    @NotBlank
    @Size(max = 120)
    private String libelle;

    private boolean actif = true;
}
