package cnm.prs.dto;

import cnm.prs.enums.CategorieModePassation;
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

    /**
     * Marqueur « appel d'offres ouvert » : si vrai, un marché de ce mode rend l'AGPM obligatoire sur le
     * PPM. Administrable (écran admin des modes) ; le front le lit sur chaque mode. {@code null} = false.
     */
    private Boolean declencheAgpm;

    /** Mode dont ce mode réutilise le modèle CAPM (null = aucun partage) — administrable. */
    private Integer idModeModeleCapm;

    /**
     * Catégorie du mode : {@code NORMAL} (droit commun) ou {@code DEROGATOIRE} ; {@code null} = non
     * classé. Déclaratif (aucune règle dérivée pour l'instant), administrable ; valeur hors enum → 400
     * ciblant le champ {@code categorie} (handler Jackson global).
     */
    private CategorieModePassation categorie;
}
