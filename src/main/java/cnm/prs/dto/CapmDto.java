package cnm.prs.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de transfert pour {@link cnm.prs.entity.Capm} (processus de marché).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CapmDto {

    private Integer idCapm;

    @Size(max = 300)
    private String libelleProcessus;

    @NotNull
    private Integer ordre;

    /** null = processus commun ; sinon spécifique au mode de passation (modèle mixte). */
    private Integer idMode;

    /** Phase du modèle (regroupement à l'affichage), null = sans phase. */
    @Size(max = 150)
    private String groupe;
}
