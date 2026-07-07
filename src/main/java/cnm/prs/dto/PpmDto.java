package cnm.prs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de transfert pour {@link cnm.prs.entity.Ppm}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PpmDto {

    private Integer idPpm;

    @NotNull
    private Integer idDossier;

    @NotNull
    private Integer exercice;

    @NotBlank
    @Size(max = 150)
    private String signataire;

    @NotNull
    private LocalDate dateSignature;

    private LocalDate datePpmInit;

    private Integer numMajPrec;

    private LocalDate dateMajPrec;

    private Integer numMaj;

    private LocalDate dateMaj;

    @NotBlank
    @Size(max = 100)
    private String reference;

    @Size(max = 200)
    private String libelle;

    private LocalDate dateReceptionCnm;

    @Size(max = 5)
    private String idLocalite;

    @Size(max = 100)
    private String vu;

    @Size(max = 10)
    private String idPrmp;

    @Size(max = 500)
    private String motifMaj;
}
