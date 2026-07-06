package cnm.prs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de transfert pour {@link cnm.prs.entity.Prmp}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrmpDto {

    /** Identifiant = matricule de la PRMP. */
    private String idPrmp;

    @NotBlank
    @Size(max = 50)
    private String nomPrmp;

    @NotBlank
    @Size(max = 100)
    private String prenomsPrmp;

    @NotBlank
    @Size(max = 100)
    private String arreteNomin;

    @NotNull
    private LocalDate dateNomin;

    @NotBlank
    @Size(max = 12)
    private String cin;

    @NotNull
    private LocalDate dateCin;

    @NotBlank
    @Size(max = 50)
    private String lieuCin;

    @NotBlank
    @Size(max = 100)
    private String emailPrmp;

    @NotBlank
    @Size(max = 20)
    private String telPrmp;
}
