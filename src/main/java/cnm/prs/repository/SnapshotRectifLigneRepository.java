package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.SnapshotRectifLigne;

@Repository
public interface SnapshotRectifLigneRepository extends JpaRepository<SnapshotRectifLigne, Integer> {

    List<SnapshotRectifLigne> findByIdDossierOrderByIdDetailAsc(Integer idDossier);

    boolean existsByIdDossierAndCycle(Integer idDossier, Integer cycle);

    /** Purge (retrait / nouveau cycle) — la table est sans FK entrante, suppression directe. */
    @Modifying
    @Query("delete from SnapshotRectifLigne s where s.idDossier = :idDossier")
    int deleteParDossier(@Param("idDossier") Integer idDossier);
}
