package cnm.prs.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.SnapshotRectifBeneficiaire;

/** ⚠️ Versions archivées (2026-09-06) — bénéficiaires figés d'une ligne archivée. Immuables. */
@Repository
public interface SnapshotRectifBeneficiaireRepository extends JpaRepository<SnapshotRectifBeneficiaire, Integer> {

    List<SnapshotRectifBeneficiaire> findByIdSnapshotInOrderByIdSnapshotBenefAsc(Collection<Integer> idSnapshots);

    /** Purge avec le circuit — avant les lignes (FK sortante vers {@code t_snapshot_rectif_ligne}). */
    @Modifying
    @Query("delete from SnapshotRectifBeneficiaire b where b.idSnapshot in "
            + "(select s.idSnapshot from SnapshotRectifLigne s where s.idDossier = :idDossier)")
    int deleteParDossier(@Param("idDossier") Integer idDossier);
}
