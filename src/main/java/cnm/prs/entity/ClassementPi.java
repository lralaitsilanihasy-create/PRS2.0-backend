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

/**
 * ⚠️ V89 (lot 3 PI, tranche PI-d2a, §B5) — le classement d'un lot : son départage motivé ({@code DEPARTAGE} : les propositions
 * techniques dans l'ordre retenu, séparées par des virgules), son arrêt par le président, sa réouverture motivée.
 */
@Entity
@Table(name = "t_evaluation_classement_pi")
@IdClass(ClassementPi.Cle.class)
@Getter
@Setter
@NoArgsConstructor
public class ClassementPi {

    @Id
    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Id
    @Column(name = "LOT", nullable = false)
    private Integer lot;

    @Column(name = "ARRETE_LE")
    private LocalDateTime arreteLe;

    @Column(name = "ARRETE_PAR", length = 100)
    private String arretePar;

    @Column(name = "OBSERVATION")
    private String observation;

    @Column(name = "ROUVERT_LE")
    private LocalDateTime rouvertLe;

    @Column(name = "MOTIF_REOUVERTURE")
    private String motifReouverture;

    @Column(name = "DEPARTAGE")
    private String departage;

    @Column(name = "MOTIF_DEPARTAGE")
    private String motifDepartage;

    @Column(name = "DEPARTAGE_PAR", length = 100)
    private String departagePar;

    @Column(name = "DEPARTAGE_LE")
    private LocalDateTime departageLe;

    public ClassementPi(Long idDmc, Integer lot) {
        this.idDmc = idDmc;
        this.lot = lot;
    }

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
