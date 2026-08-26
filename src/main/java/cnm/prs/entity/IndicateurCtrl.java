package cnm.prs.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entité JPA mappée sur la table {@code t_indicateur_ctrl}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "t_indicateur_ctrl")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IndicateurCtrl {

    @Id
    @Column(name = "ID_INDICATEUR", nullable = false)
    private Integer idIndicateur;

    @Column(name = "IM_CONTROLEUR", nullable = false, length = 7)
    private String imControleur;

    @Column(name = "PERIODE", nullable = false, length = 7)
    private String periode;

    @Column(name = "NB_EXAMENS")
    private Integer nbExamens;

    @Column(name = "NB_CONFORMES")
    private Integer nbConformes;

    @Column(name = "DELAI_MOYEN_EXAMEN")
    private BigDecimal delaiMoyenExamen;

    @Column(name = "NB_OBS_EMISES")
    private Integer nbObsEmises;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "IM_CONTROLEUR", insertable = false, updatable = false)
    @JsonIgnore
    private Controleur controleur;
}
