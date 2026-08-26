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
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entité JPA mappée sur la table {@code t_snapshot_stats}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "t_snapshot_stats")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotStats {

    @Id
    @Column(name = "ID_SNAPSHOT", nullable = false)
    private Integer idSnapshot;

    @Column(name = "DATE_SNAPSHOT", nullable = false)
    private LocalDate dateSnapshot;

    @Column(name = "ID_LOCALITE", length = 5)
    private String idLocalite;

    @Column(name = "EXERCICE", nullable = false)
    private Integer exercice;

    @Column(name = "NB_DOSSIERS_RECUS")
    private Integer nbDossiersRecus;

    @Column(name = "NB_DOSSIERS_CLOTURES")
    private Integer nbDossiersClotures;

    @Column(name = "NB_DOSSIERS_EN_COURS")
    private Integer nbDossiersEnCours;

    @Column(name = "TAUX_CONFORMITE")
    private BigDecimal tauxConformite;

    @Column(name = "DELAI_MOYEN_JOURS")
    private BigDecimal delaiMoyenJours;

    @Column(name = "MONT_TOTAL_CONTROLE")
    private BigDecimal montTotalControle;

    @Column(name = "NB_RETOURS_MOYEN")
    private BigDecimal nbRetoursMoyen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_LOCALITE", insertable = false, updatable = false)
    @JsonIgnore
    private Localite localite;
}
