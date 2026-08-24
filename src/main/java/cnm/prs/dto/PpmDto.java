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

    /**
     * Rattachement figé au dossier. Exigé en création/mise à jour ({@link GroupesValidation.Identite}) ;
     * non exigé en rectification, où le serveur conserve la valeur existante.
     */
    @NotNull(groups = GroupesValidation.Identite.class)
    private Integer idDossier;

    @NotNull
    private Integer exercice;

    @NotBlank
    @Size(max = 210)
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

    /**
     * <strong>Dérivé serveur (lecture seule)</strong> : {@code true} ssi ≥1 marché de ce PPM est en
     * « appel d'offres ouvert » ({@code ModePassation.declencheAgpm}). Indique au front qu'un AGPM
     * (Avis Général de Passation de Marché) doit accompagner le PPM. Le front l'<em>affiche</em>, ne le
     * recalcule pas ; toute valeur envoyée en écriture est ignorée.
     */
    private Boolean agpmRequis;
}
