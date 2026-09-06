package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.SnapshotRectifLigne;

/**
 * Lignes figées des versions archivées d'un dossier. Une ligne ne s'écrit qu'à l'archivage : aucune
 * méthode de mise à jour, l'entité est immuable (⚠️ versions archivées, 2026-09-06).
 */
@Repository
public interface SnapshotRectifLigneRepository extends JpaRepository<SnapshotRectifLigne, Integer> {

    /** Lignes d'une version, dans l'ordre des lignes de marché (le diff et la restitution s'y appuient). */
    List<SnapshotRectifLigne> findByIdVersionOrderByIdDetailAsc(Integer idVersion);

    /** Purge avec le circuit — après les enfants (bénéficiaires, lots, prévisions), avant les en-têtes. */
    @Modifying
    @Query("delete from SnapshotRectifLigne s where s.idDossier = :idDossier")
    int deleteParDossier(@Param("idDossier") Integer idDossier);
}
