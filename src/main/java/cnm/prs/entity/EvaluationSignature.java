package cnm.prs.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ V78 (§B6) — la signature du rapport d'évaluation par un membre de la CAO (signature électronique simple, observation en cas de
 * désaccord), ou son empêchement constaté par le président.
 */
@Entity
@Table(name = "t_evaluation_signature")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationSignature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "IM", nullable = false, length = 20)
    private String im;

    @Column(name = "DATE", nullable = false)
    private LocalDateTime date;

    @Column(name = "EMPECHEMENT", nullable = false)
    private Boolean empechement = Boolean.FALSE;

    @Column(name = "MOTIF")
    private String motif;

    @Column(name = "CONSTATE_PAR", length = 100)
    private String constatePar;

    @Column(name = "OBSERVATION")
    private String observation;
}
