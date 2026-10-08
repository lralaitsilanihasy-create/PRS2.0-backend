package cnm.prs.entity;

import java.math.BigDecimal;

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
 * ⚠️ V84 (lot 3 PI, tranche PI-a, arbitrage Q3 du pilote) — un sous-critère technique d'une version de fiche de prestations
 * intellectuelles : son critère ({@code B06-TP-02} à {@code B06-TP-06}), son rang, son libellé, ses points.
 */
@Entity
@Table(name = "t_fiche_sous_critere")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FicheSousCritere {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_SOUS_CRITERE", nullable = false)
    private Integer idSousCritere;

    @Column(name = "ID_FICHE", nullable = false)
    private Integer idFiche;

    @Column(name = "CRITERE", nullable = false, length = 20)
    private String critere;

    @Column(name = "ORDRE", nullable = false)
    private Integer ordre;

    @Column(name = "LIBELLE", nullable = false, length = 300)
    private String libelle;

    @Column(name = "POINTS", nullable = false, precision = 7, scale = 2)
    private BigDecimal points;
}
