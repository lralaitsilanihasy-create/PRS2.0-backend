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
 * ⚠️ V76 (§P3) — une décision de la CAO sur une offre, à une étape : la décision ({@code CONFORME} / {@code ECARTEE} à l'examen
 * préliminaire), la qualification, le motif, la clause du DAO, et le détail de l'étape en JSON ({@code contenu} : la grille des
 * vérifications). En ajout seul : une décision corrigée est marquée {@code remplaceeLe}, jamais effacée.
 */
@Entity
@Table(name = "t_evaluation_decision")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationDecision {

    public static final String CONFORME = "CONFORME";
    public static final String ECARTEE = "ECARTEE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "ID_OFFRE", nullable = false, length = 36)
    private String idOffre;

    @Column(name = "LOT", nullable = false)
    private Integer lot;

    @Column(name = "ETAPE", nullable = false, length = 20)
    private String etape;

    @Column(name = "DECISION", nullable = false, length = 20)
    private String decision;

    @Column(name = "QUALIFICATION", length = 20)
    private String qualification;

    @Column(name = "MOTIF")
    private String motif;

    @Column(name = "CLAUSE", length = 300)
    private String clause;

    @Column(name = "CONTENU")
    private String contenu;

    @Column(name = "PAR", nullable = false, length = 20)
    private String par;

    @Column(name = "LE", nullable = false)
    private LocalDateTime le;

    @Column(name = "REMPLACEE_LE")
    private LocalDateTime remplaceeLe;
}
