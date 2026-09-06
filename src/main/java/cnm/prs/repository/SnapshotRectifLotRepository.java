package cnm.prs.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.SnapshotRectifLot;

/** ⚠️ Versions archivées (2026-09-06) — lots figés d'une ligne archivée. Immuables. */
@Repository
public interface SnapshotRectifLotRepository extends JpaRepository<SnapshotRectifLot, Integer> {

    List<SnapshotRectifLot> findByIdSnapshotInOrderByIdSnapshotLotAsc(Collection<Integer> idSnapshots);

    /** Purge avec le circuit — avant les lignes (FK sortante vers {@code t_snapshot_rectif_ligne}). */
    @Modifying
    @Query("delete from SnapshotRectifLot l where l.idSnapshot in "
            + "(select s.idSnapshot from SnapshotRectifLigne s where s.idDossier = :idDossier)")
    int deleteParDossier(@Param("idDossier") Integer idDossier);
}
