package cnm.prs.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de transfert pour {@link cnm.prs.entity.Dossier}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DossierDto {

    private Integer idDossier;

    @Size(max = 10)
    private String idTypeDossier;

    /**
     * ⚠️ Règle ajoutée — sous-type du dossier (référentiel {@code /api/sous-type-dossiers}), la famille
     * ({@code idTypeDossier}) s'en déduit. Famille DDP : dérivé serveur ({@code PPM} / {@code PPM-AGPM}),
     * toute valeur envoyée est ignorée ; familles DMC/DDM : choisi à la saisie.
     */
    @Size(max = 20)
    private String idSousType;

    private Integer idDossierParent;

    @Size(max = 100)
    private String refeDossier;

    private LocalDate dateRef;

    @Size(max = 20)
    private String statut;

    @Size(max = 5)
    private String idLocalite;

    @Size(max = 10)
    private String idPrmp;

    private Integer idEntiteContract;
}
