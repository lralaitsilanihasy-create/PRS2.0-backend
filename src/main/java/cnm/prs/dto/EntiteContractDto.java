package cnm.prs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de transfert pour {@link cnm.prs.entity.EntiteContract}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EntiteContractDto {

    private Integer idEntiteContract;

    @NotBlank
    @Size(max = 150)
    private String libelleEntite;

    @NotBlank
    @Size(max = 200)
    private String adresse;

    @Size(max = 20)
    private String categorieEntite;

    @NotNull
    private Integer idOrganigramme;

    private Integer idEntiteParent;

    private Integer niveauHierarchique;

    @Size(max = 5)
    private String idLocalite;
}
