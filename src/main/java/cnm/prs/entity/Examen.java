package cnm.prs.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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

    /**
     * Verrou optimiste (⚠️ audit 2026-08-27, lot D §7, migration V9) : l'examen est l'écriture la plus
     * concurrente du circuit — plusieurs acteurs le remplissent point par point. Une écriture perdante
     * lève un 409 {@code CONFLIT_VERSION} au lieu d'écraser silencieusement l'avis d'un autre.
     * Le champ ne remonte <strong>pas</strong> dans {@code ExamenDto} : le contrat HTTP est inchangé.
     */
    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

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
