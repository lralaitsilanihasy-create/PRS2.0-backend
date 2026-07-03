package cnm.prs.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de transfert pour {@link cnm.prs.entity.ServiceBeneficiaire}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceBeneficiaireDto {

    private Integer idBenef;

    private BigDecimal ancMontBenef;

    private BigDecimal nouvMontBenef;

    @Size(max = 25)
    private String soaCode;

    /** Compte budgétaire du bénéficiaire (FK {@code tr_compte}) — compte et montant sont par bénéficiaire. */
    @Size(max = 20)
    private String numCompte;

    @NotNull
    private Integer idDetail;
}
