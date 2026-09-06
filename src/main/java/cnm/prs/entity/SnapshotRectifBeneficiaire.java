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
 * ⚠️ Versions archivées (2026-09-06) — un service bénéficiaire d'une ligne figée
 * ({@link SnapshotRectifLigne}), copié de {@link ServiceBeneficiaire} au moment du gel. Immuable.
 */
@Entity
@Table(name = "t_snapshot_rectif_beneficiaire")
@Immutable
@Getter
@Setter
@NoArgsConstructor
public class SnapshotRectifBeneficiaire {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_SNAPSHOT_BENEF", nullable = false)
    private Integer idSnapshotBenef;

    @Column(name = "ID_SNAPSHOT", nullable = false)
    private Integer idSnapshot;

    @Column(name = "SOA_CODE", length = 25)
    private String soaCode;

    @Column(name = "NUM_COMPTE", length = 20)
    private String numCompte;

    @Column(name = "ANC_MONT_BENEF")
    private BigDecimal ancMontBenef;

    @Column(name = "NOUV_MONT_BENEF")
    private BigDecimal nouvMontBenef;
}
