package cnm.prs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de transfert pour {@link cnm.prs.entity.PointsCtrl}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PointsCtrlDto {

    private Integer idPointCtrl;

    private String libelPointCtrl;

    private String decriptPointCtrl;

    private Integer ordrePointCtrl;

    @NotNull
    private Boolean obligatoire;

    @NotBlank
    private String idTypeDossier;

    /**
     * ⚠️ Règle ajoutée — sous-type ciblé (facultatif) : {@code null} = point commun à toute la famille ;
     * renseigné = point spécifique à ce sous-type (doit appartenir à la famille {@code idTypeDossier},
     * sinon 400). Dropdown admin : {@code GET /api/sous-type-dossiers/par-famille/{famille}}.
     */
    @Size(max = 20)
    private String idSousType;

    /**
     * ⚠️ Règle ajoutée (2026-07-21) — portée du point : {@code LIGNE} (évalué par ligne de marché) ou
     * {@code DOSSIER} (inter-lignes, ex. fractionnement illicite). Le front lit ce champ pour placer chaque
     * point (par ligne / au niveau dossier). Optionnel en entrée (absent → défaut {@code LIGNE},
     * code inconnu → 400 ciblé) ; toujours renseigné en sortie.
     */
    @Size(max = 10)
    private String portee;
}
