package cnm.prs.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entité JPA mappée sur la table {@code t_pv_navette}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "t_pv_navette")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PvNavette {

    @Id
    @Column(name = "ID_NAVETTE", nullable = false)
    private Integer idNavette;

    @Column(name = "ID_PV", nullable = false)
    private Integer idPv;

    @Column(name = "NUM_NAVETTE", nullable = false)
    private Integer numNavette;

    @Column(name = "SENS", nullable = false, length = 20)
    private String sens;

    @Column(name = "IM_ACTEUR", nullable = false, length = 7)
    private String imActeur;

    @Column(name = "DATE_ACTION", nullable = false)
    private LocalDateTime dateAction;

    @Column(name = "COMMENTAIRE", columnDefinition = "text")
    private String commentaire;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_PV", insertable = false, updatable = false)
    @JsonIgnore
    private PvExamen pv;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "IM_ACTEUR", insertable = false, updatable = false)
    @JsonIgnore
    private Controleur acteur;
}
