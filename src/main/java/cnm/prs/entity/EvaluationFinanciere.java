package cnm.prs.entity;

import java.math.BigDecimal;
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
 * ⚠️ V89 (lot 3 PI, tranche PI-d2a, §B5) — la saisie d'une proposition financière ouverte : prix lu hors taxes, corrections
 * arithmétiques (JSON), prix corrigé, dépenses remboursables (arbitrage du pilote : saisies par la commission) ; ou le refus d'une
 * correction par le candidat, qui l'écarte. Une saisie par proposition, remplacée par la suivante (le journal les garde toutes).
 */
@Entity
@Table(name = "t_evaluation_financiere")
@Getter
@Setter
@NoArgsConstructor
public class EvaluationFinanciere {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "ID_OFFRE", nullable = false, length = 36)
    private String idOffre;

    @Column(name = "LOT")
    private Integer lot;

    @Column(name = "PRIX_LU", nullable = false)
    private BigDecimal prixLu;

    @Column(name = "PRIX_LU_TTC")
    private BigDecimal prixLuTtc;

    @Column(name = "CORRECTIONS")
    private String corrections;

    @Column(name = "PRIX_CORRIGE", nullable = false)
    private BigDecimal prixCorrige;

    @Column(name = "REMBOURSABLES", nullable = false)
    private BigDecimal remboursables;

    @Column(name = "MOTIF_REMBOURSABLES")
    private String motifRemboursables;

    @Column(name = "REFUS_MOTIF")
    private String refusMotif;

    @Column(name = "REFUS_CLAUSE")
    private String refusClause;

    @Column(name = "PAR", nullable = false, length = 100)
    private String par;

    @Column(name = "LE", nullable = false)
    private LocalDateTime le;
}
