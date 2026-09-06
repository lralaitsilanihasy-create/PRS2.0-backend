package cnm.prs.entity;

import java.time.LocalDate;

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
 * ⚠️ Versions archivées (2026-09-06) — une date prévisionnelle (processus {@code idCapm}) d'une ligne
 * figée ({@link SnapshotRectifLigne}), copiée de {@link MarchePrevision} au moment du gel. Immuable.
 */
@Entity
@Table(name = "t_snapshot_rectif_prevision")
@Immutable
@Getter
@Setter
@NoArgsConstructor
public class SnapshotRectifPrevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_SNAPSHOT_PREV", nullable = false)
    private Integer idSnapshotPrev;

    @Column(name = "ID_SNAPSHOT", nullable = false)
    private Integer idSnapshot;

    @Column(name = "ID_CAPM", nullable = false)
    private Integer idCapm;

    @Column(name = "DATE_DEBUT")
    private LocalDate dateDebut;

    @Column(name = "DATE_FIN")
    private LocalDate dateFin;
}
