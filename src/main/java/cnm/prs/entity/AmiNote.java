package cnm.prs.entity;

import java.math.BigDecimal;
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
 * ⚠️ V83 (AMI en ligne, tranche AMI-b, §B3) — la note retenue par la commission pour une expression d'intérêt sur un critère publié
 * (de 0 au poids du critère), avec son motif ; une seule par expression et par critère (une nouvelle saisie la remplace, le journal
 * garde la trace).
 */
@Entity
@Table(name = "t_ami_note")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AmiNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "ID_EXPRESSION", nullable = false, length = 36)
    private String idExpression;

    @Column(name = "CODE_CRITERE", nullable = false, length = 20)
    private String codeCritere;

    @Column(name = "NOTE", nullable = false, precision = 6, scale = 2)
    private BigDecimal note;

    @Column(name = "MOTIF")
    private String motif;

    @Column(name = "PAR", length = 100)
    private String par;

    @Column(name = "LE", nullable = false)
    private LocalDateTime le;
}
