package cnm.prs.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de transfert pour {@link cnm.prs.entity.MarchePrevision} : date prévisionnelle d'un marché
 * pour un processus ({@code idCapm}). {@code ordre} est en lecture seule (porté par {@code t_capm}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarchePrevisionDto {

    @NotNull
    private Integer idPrevision;

    @NotNull
    private Integer idDetail;

    @NotNull
    private Integer idCapm;

    @NotNull
    private LocalDate dateDebut;

    /** dateFin OPTIONNELLE (fin non connue / ouverte) ; chronologie vérifiée seulement si présente. */
    private LocalDate dateFin;

    /** Ordre d'affichage du processus, porté par {@code t_capm.ORDRE} (lecture seule). */
    private Integer ordre;
}
