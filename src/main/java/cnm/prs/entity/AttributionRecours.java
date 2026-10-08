package cnm.prs.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ V81 (évaluation des offres, lot 2, tranche 2c ; arbitrage Q5 du pilote, loi n° 2016-055 titre VIII) — un recours reçu par la PRMP
 * sur l'attribution d'un lot, et sa décision : {@code REEXAMEN} (art. 79, non suspensif, réponse sous 10 jours), {@code REVISION_ARMP}
 * (art. 80) et {@code REFERE} (art. 78), qui suspendent la signature jusqu'à la décision, 20 jours au plus.
 */
@Entity
@Table(name = "t_attribution_recours")
@Getter
@Setter
@NoArgsConstructor
public class AttributionRecours {

    public static final String REEXAMEN = "REEXAMEN";
    public static final String REVISION_ARMP = "REVISION_ARMP";
    public static final String REFERE = "REFERE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "LOT", nullable = false)
    private Integer lot;

    @Column(name = "TYPE", nullable = false, length = 20)
    private String type;

    @Column(name = "DATE_RECEPTION", nullable = false)
    private LocalDate dateReception;

    @Column(name = "REQUERANT", nullable = false, length = 255)
    private String requerant;

    @Column(name = "OBJET", nullable = false)
    private String objet;

    @Column(name = "DECLARE_LE", nullable = false)
    private LocalDateTime declareLe;

    @Column(name = "DECLARE_PAR", length = 100)
    private String declarePar;

    @Column(name = "DATE_DECISION")
    private LocalDate dateDecision;

    @Column(name = "ISSUE", length = 20)
    private String issue;

    @Column(name = "MOTIF_DECISION")
    private String motifDecision;

    @Column(name = "DECIDE_LE")
    private LocalDateTime decideLe;

    @Column(name = "DECIDE_PAR", length = 100)
    private String decidePar;

    /** ⚠️ V91 (2d-1, §B7) — l'alerte de l'échéance d'un réexamen, déjà émise à la PRMP. */
    @Column(name = "ALERTE_LE")
    private LocalDateTime alerteLe;
}
