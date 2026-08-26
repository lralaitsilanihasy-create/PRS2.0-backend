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
 * Entité JPA mappée sur la table {@code t_tranche}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "t_tranche")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Tranche {

    @Id
    @Column(name = "ID_TRANCHE", nullable = false)
    private Integer idTranche;

    @Column(name = "LIEU_TRC", length = 100)
    private String lieuTrc;

    @Column(name = "MONT_TRC")
    private BigDecimal montTrc;

    @Column(name = "ID_LOT", nullable = false)
    private Integer idLot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_LOT", insertable = false, updatable = false)
    @JsonIgnore
    private Lot lot;
}
