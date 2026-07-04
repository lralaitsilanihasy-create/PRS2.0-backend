package cnm.prs.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de transfert pour {@link cnm.prs.entity.Marche}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarcheDto {

    private Integer idDetail;

    @NotNull
    private Integer idDossier;

    @NotNull
    private Integer idPpm;

    @Size(max = 500)
    private String designationMarche;

    @Size(max = 20)
    private String numCompte;

    private BigDecimal montEstim;

    private BigDecimal ancienMontEstim;

    private BigDecimal nouvMontEstim;

    @Size(max = 20)
    private String financement;

    @Size(max = 20)
    private String statut;

    private Integer idNature;

    private Integer idMode;
}
