package cnm.prs.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de lecture pour {@link cnm.prs.entity.Mandat}.
 *
 * <p>{@code statut} est la valeur <strong>dérivée à la date du jour</strong> (cf.
 * {@link cnm.prs.enums.StatutMandat}), pas nécessairement celle stockée lors de la dernière écriture.
 * {@code implicite} vaut {@code true} pour le mandat reconstitué depuis {@code t_prmp}
 * ({@code ARRETE_NOMIN} / {@code DATE_NOMIN}) quand aucun mandat n'a encore été déclaré pour cette
 * PRMP — il n'a alors pas d'{@code idMandat}.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MandatDto {

    private Integer idMandat;

    private String idPrmp;

    private String titulaire;

    private LocalDate dateDebut;

    private LocalDate dateFin;

    private String refArrete;

    private String statut;

    private Integer numeroMandat;

    private LocalDate dateAbrogation;

    private String motifAbrogation;

    /** Vrai si le mandat est reconstitué depuis {@code t_prmp} (aucun mandat déclaré). */
    private boolean implicite;
}
