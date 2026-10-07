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
 * ⚠️ V76 (§B1) — l'arrêt d'une étape de l'évaluation d'un lot, par le président de la CAO. En vigueur tant que {@code rouverteLe} est
 * nul ; rouvert, il reste au registre avec la date, l'auteur et le motif de sa réouverture.
 */
@Entity
@Table(name = "t_evaluation_etape")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationEtape {

    public static final String CONFORMITE = "CONFORMITE";
    public static final String EVALUATION = "EVALUATION";
    public static final String ANORMALES = "ANORMALES";
    public static final String QUALIFICATION = "QUALIFICATION";
    /** L'étape d'un lot dont les quatre étapes sont arrêtées : le rapport. */
    public static final String RAPPORT = "RAPPORT";
    /** Les étapes, dans l'ordre du guide (étapes 2 à 5). */
    public static final java.util.List<String> ORDRE = java.util.List.of(CONFORMITE, EVALUATION, ANORMALES, QUALIFICATION);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "LOT", nullable = false)
    private Integer lot;

    @Column(name = "ETAPE", nullable = false, length = 20)
    private String etape;

    @Column(name = "ARRETEE_LE", nullable = false)
    private LocalDateTime arreteeLe;

    @Column(name = "ARRETEE_PAR", nullable = false, length = 20)
    private String arreteePar;

    @Column(name = "OBSERVATION")
    private String observation;

    @Column(name = "ROUVERTE_LE")
    private LocalDateTime rouverteLe;

    @Column(name = "ROUVERTE_PAR", length = 20)
    private String rouvertePar;

    @Column(name = "MOTIF_REOUVERTURE")
    private String motifReouverture;
}
