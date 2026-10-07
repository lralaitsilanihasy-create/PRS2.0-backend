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
 * ⚠️ V76 (§P6) — la déclaration préalable d'un membre de la CAO : absence de conflit d'intérêts et confidentialité (art. 21-d, 12-V).
 * Une par membre et par procédure ; un membre qui déclare un conflit ne décide rien.
 */
@Entity
@Table(name = "t_evaluation_declaration")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationDeclaration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "IM", nullable = false, length = 20)
    private String im;

    @Column(name = "SIGNEE_LE", nullable = false)
    private LocalDateTime signeeLe;

    @Column(name = "CONFLIT", nullable = false)
    private Boolean conflit = Boolean.FALSE;

    @Column(name = "PRECISION")
    private String precision;
}
