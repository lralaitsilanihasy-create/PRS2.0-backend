package cnm.prs.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entité JPA mappée sur la table {@code t_examen}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "t_examen")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Examen {

    @Id
    @Column(name = "ID_EXAMEN", nullable = false)
    private Integer idExamen;

    @Column(name = "ID_DISPATCH", nullable = false)
    private Integer idDispatch;

    @Column(name = "IM_CTRL_MEMBRE", length = 7)
    private String imCtrlMembre;

    @Column(name = "DATE_EXAMEN")
    private LocalDate dateExamen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_DISPATCH", insertable = false, updatable = false)
    @JsonIgnore
    private Dispatch dispatch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "IM_CTRL_MEMBRE", insertable = false, updatable = false)
    @JsonIgnore
    private Controleur ctrlMembre;
}
