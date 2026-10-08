package cnm.prs.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ V93 (lot 2, tranche 2d-3, §B6, Q10 ; art. 55) — une demande de déclaration sans suite : les motifs de la PRMP, le dossier
 * {@code DSS} soumis à la Commission, l'avis constaté ({@code FAV} ou {@code DEF}), puis la déclaration (décision de la PRMP) après un
 * avis favorable. Un avis défavorable laisse la procédure reprendre son cours ; une nouvelle demande reste possible.
 */
@Entity
@Table(name = "t_sans_suite")
@Getter
@Setter
@NoArgsConstructor
public class SansSuite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "MOTIFS", nullable = false)
    private String motifs;

    @Column(name = "DEMANDE_LE", nullable = false)
    private LocalDateTime demandeLe;

    @Column(name = "DEMANDE_PAR", length = 100)
    private String demandePar;

    @Column(name = "ID_DOSSIER")
    private Integer idDossier;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "MOTIFS_PDF")
    private byte[] motifsPdf;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "MOTIFS_DOCX")
    private byte[] motifsDocx;

    @Column(name = "AVIS", length = 10)
    private String avis;

    @Column(name = "AVIS_CONSTATE_LE")
    private LocalDateTime avisConstateLe;

    @Column(name = "ALERTE_LE")
    private LocalDateTime alerteLe;

    @Column(name = "DECISION_REFERENCE", length = 100)
    private String decisionReference;

    @Column(name = "DECISION_DATE")
    private LocalDate decisionDate;

    @Column(name = "DECLARE_LE")
    private LocalDateTime declareLe;

    @Column(name = "DECLARE_PAR", length = 100)
    private String declarePar;
}
