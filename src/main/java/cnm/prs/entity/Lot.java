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
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entité JPA mappée sur la table {@code t_lot}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "t_lot")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Lot {

    @Id
    @Column(name = "ID_LOT", nullable = false)
    private Integer idLot;

    @Column(name = "ID_DOSSIER", nullable = false)
    private Integer idDossier;

    @Column(name = "ID_DETAIL", nullable = false)
    private Integer idDetail;

    @Column(name = "DESIGNATION_LOT", nullable = false, length = 200)
    private String designationLot;

    @Column(name = "MONT_LOT")
    private BigDecimal montLot;

    @Column(name = "QTE_LOT")
    private Integer qteLot;

    @Column(name = "UNITE_LOT", length = 10)
    private String uniteLot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_DETAIL", insertable = false, updatable = false)
    @JsonIgnore
    private Marche detail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_DOSSIER", insertable = false, updatable = false)
    @JsonIgnore
    private Dossier dossier;
}
