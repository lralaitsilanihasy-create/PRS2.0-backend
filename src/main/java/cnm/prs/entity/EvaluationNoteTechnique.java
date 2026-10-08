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
 * ⚠️ V87 (lot 3 PI, tranche PI-c, Q4) — la note d'un membre de la commission sur un élément de la grille technique (un sous-critère de
 * la fiche, {@code B06-TP-03#2}, ou un critère noté globalement, {@code B06-TP-02}) d'une proposition technique, motivée.
 */
@Entity
@Table(name = "t_evaluation_note_technique")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationNoteTechnique {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "ID_OFFRE", nullable = false, length = 36)
    private String idOffre;

    @Column(name = "IM", nullable = false, length = 20)
    private String im;

    @Column(name = "ELEMENT", nullable = false, length = 30)
    private String element;

    @Column(name = "NOTE", nullable = false, precision = 7, scale = 2)
    private BigDecimal note;

    @Column(name = "MOTIF", nullable = false)
    private String motif;

    @Column(name = "LE", nullable = false)
    private LocalDateTime le;
}
