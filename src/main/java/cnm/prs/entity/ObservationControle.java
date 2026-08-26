package cnm.prs.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entité JPA mappée sur la table {@code t_observation_controle} : une ligne « AU LIEU DE / LIRE »
 * d'observation d'un point de contrôle d'examen ({@link ExamenDetail}, via {@code ID_DETAIL}).
 * Relation 1,N : un point de contrôle a 0..N lignes d'observation. PK auto (IDENTITY).
 */
@Entity
@Table(name = "t_observation_controle")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ObservationControle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_OBSERVATION", nullable = false)
    private Integer idObservation;

    /** Point de contrôle concerné (FK vers {@code t_examen_detail.ID_DETAIL_EXAMEN}). */
    @Column(name = "ID_DETAIL", nullable = false)
    private Integer idDetail;

    @Column(name = "AU_LIEU_DE", length = 500)
    private String auLieuDe;

    @Column(name = "LIRE", length = 500)
    private String lire;

    /** Ordre de saisie (ASC). */
    @Column(name = "ORDRE")
    private Integer ordre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_DETAIL", insertable = false, updatable = false)
    @JsonIgnore
    private ExamenDetail detail;
}
