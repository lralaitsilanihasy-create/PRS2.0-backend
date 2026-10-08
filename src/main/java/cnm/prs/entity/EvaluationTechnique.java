package cnm.prs.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** ⚠️ V87 (lot 3 PI, tranche PI-c) — l'étape technique d'un lot : son arrêt par le président, et sa réouverture motivée. */
@Entity
@Table(name = "t_evaluation_technique")
@IdClass(EvaluationTechnique.Cle.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationTechnique {

    @Id
    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Id
    @Column(name = "LOT", nullable = false)
    private Integer lot;

    @Column(name = "ARRETEE_LE")
    private LocalDateTime arreteeLe;

    @Column(name = "ARRETEE_PAR", length = 100)
    private String arreteePar;

    @Column(name = "OBSERVATION")
    private String observation;

    @Column(name = "ROUVERTE_LE")
    private LocalDateTime rouverteLe;

    @Column(name = "MOTIF_REOUVERTURE")
    private String motifReouverture;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Cle implements Serializable {
        private Long idDmc;
        private Integer lot;
    }
}
