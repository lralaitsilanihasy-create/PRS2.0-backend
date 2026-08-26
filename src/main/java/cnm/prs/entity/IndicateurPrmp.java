package cnm.prs.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entité JPA mappée sur la table {@code t_indicateur_prmp}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "t_indicateur_prmp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IndicateurPrmp {

    @Id
    @Column(name = "ID_INDICATEUR_PRMP", nullable = false)
    private Integer idIndicateurPrmp;

    @Column(name = "ID_PRMP", nullable = false, length = 10)
    private String idPrmp;

    @Column(name = "EXERCICE", nullable = false)
    private Integer exercice;

    @Column(name = "NB_PPM_SOUMIS", nullable = false)
    private Integer nbPpmSoumis;

    @Column(name = "NB_DOSSIERS_SOUMIS", nullable = false)
    private Integer nbDossiersSoumis;

    @Column(name = "NB_DOSSIERS_CONFORMES", nullable = false)
    private Integer nbDossiersConformes;

    @Column(name = "NB_DOSSIERS_NON_CONFORMES", nullable = false)
    private Integer nbDossiersNonConformes;

    @Column(name = "NB_RETOURS", nullable = false)
    private Integer nbRetours;

    @Column(name = "NB_RETRAITS", nullable = false)
    private Integer nbRetraits;

    @Column(name = "TAUX_CONFORMITE")
    private BigDecimal tauxConformite;

    @Column(name = "DELAI_MOY_CORRECTION_JOURS")
    private BigDecimal delaiMoyCorrectionJours;

    @Column(name = "MONT_TOTAL_SOUMIS")
    private BigDecimal montTotalSoumis;

    @Column(name = "DATE_MAJ")
    private LocalDateTime dateMaj;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_PRMP", insertable = false, updatable = false)
    @JsonIgnore
    private Prmp prmp;
}
