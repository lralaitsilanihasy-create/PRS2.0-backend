package cnm.prs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

    /**
     * ⚠️ V48 (2026-09-26) — sigle de l'entité (segment de référence) : facultatif, 20 caractères au plus, lettres,
     * chiffres, tirets et points seulement, sans espace ; vide = absent.
     */
    @Size(max = 20)
    @Pattern(regexp = "[A-Za-z0-9.-]*", message = "Le sigle n'admet que des lettres, des chiffres, des tirets et des points, sans espace.")
    private String sigle;
}
