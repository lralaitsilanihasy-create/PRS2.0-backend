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
 * ⚠️ V77 (évaluation des offres, §B3.6) — le départage, par la CAO, d'offres d'un lot classées à égalité de montant évalué :
 * {@code ordre} (identifiants d'offres séparés par des virgules, du premier au dernier) et son motif. En vigueur tant que
 * {@code remplaceLe} est nul.
 */
@Entity
@Table(name = "t_evaluation_departage")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationDepartage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "LOT", nullable = false)
    private Integer lot;

    @Column(name = "ORDRE", nullable = false, length = 400)
    private String ordre;

    @Column(name = "MOTIF", nullable = false)
    private String motif;

    @Column(name = "PAR", nullable = false, length = 20)
    private String par;

    @Column(name = "LE", nullable = false)
    private LocalDateTime le;

    @Column(name = "REMPLACE_LE")
    private LocalDateTime remplaceLe;
}
