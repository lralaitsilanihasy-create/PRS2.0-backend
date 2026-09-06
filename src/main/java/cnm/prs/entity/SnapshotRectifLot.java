package cnm.prs.entity;

import java.math.BigDecimal;

import org.hibernate.annotations.Immutable;

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
 * ⚠️ Versions archivées (2026-09-06) — un lot d'une ligne figée ({@link SnapshotRectifLigne}), copié
 * de {@link Lot} au moment du gel. Immuable.
 */
@Entity
@Table(name = "t_snapshot_rectif_lot")
@Immutable
@Getter
@Setter
@NoArgsConstructor
public class SnapshotRectifLot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_SNAPSHOT_LOT", nullable = false)
    private Integer idSnapshotLot;

    @Column(name = "ID_SNAPSHOT", nullable = false)
    private Integer idSnapshot;

    @Column(name = "DESIGNATION_LOT", length = 200)
    private String designationLot;

    @Column(name = "MONT_LOT")
    private BigDecimal montLot;

    @Column(name = "QTE_LOT")
    private Integer qteLot;

    @Column(name = "UNITE_LOT", length = 10)
    private String uniteLot;
}
