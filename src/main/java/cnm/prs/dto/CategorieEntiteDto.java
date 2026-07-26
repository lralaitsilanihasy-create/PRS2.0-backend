package cnm.prs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de transfert pour {@link cnm.prs.entity.CategorieEntite} (référentiel {@code tr_categorie_entite}).
 * {@code libelle} est la PK (identifiant de ressource) ; {@code niveauHierarchique} = niveau dérivé pour les
 * entités de cette catégorie.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategorieEntiteDto {

    @NotBlank
    @Size(max = 20)
    private String libelle;

    @NotNull
    @Positive
    private Integer niveauHierarchique;
}
