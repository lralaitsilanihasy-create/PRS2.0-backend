package cnm.prs.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ V125 (lot MS-2) — un titulaire consulté par une remise en concurrence (tous le sont : décret n° 2019-1064, art. 21) : la
 * notification de l'invitation et, à l'évaluation, le constat de l'absence de réponse (art. 22).
 */
@Entity
@Table(name = "t_remise_consulte")
@IdClass(RemiseConsulte.Cle.class)
@Getter
@Setter
@NoArgsConstructor
public class RemiseConsulte {

    @Id
    @Column(name = "ID_REMISE", nullable = false)
    private Long idRemise;

    @Id
    @Column(name = "ID_OFFRE", nullable = false, length = 36)
    private String idOffre;

    @Column(name = "NOTIFIE_LE", nullable = false)
    private LocalDateTime notifieLe;

    @Column(name = "SANS_REPONSE", nullable = false)
    private boolean sansReponse;

    @Getter
    @Setter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Cle implements Serializable {
        private Long idRemise;
        private String idOffre;
    }
}
