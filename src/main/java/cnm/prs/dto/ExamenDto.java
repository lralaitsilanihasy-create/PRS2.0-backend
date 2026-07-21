package cnm.prs.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de transfert pour {@link cnm.prs.entity.Examen}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExamenDto {

    private Integer idExamen;

    @NotNull
    private Integer idDispatch;

    @Size(max = 7)
    private String imCtrlMembre;

    private LocalDate dateExamen;

    /**
     * ⚠️ Règle ajoutée (2026-07-21) — <strong>avis suggéré</strong> (lecture seule, non contraignant) :
     * {@code DEF} (défavorable) si au moins un point de la grille est non conforme, sinon {@code FAV}
     * (favorable) ; {@code null} si aucun point n'est encore évalué. Le membre reste maître de l'avis
     * final saisi à la soumission ({@code idAvis}) — cette valeur ne sert qu'à pré-remplir le front.
     */
    private String avisSuggere;
}
