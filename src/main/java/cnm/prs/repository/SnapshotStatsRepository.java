package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.SnapshotStats;

@Repository
public interface SnapshotStatsRepository extends JpaRepository<SnapshotStats, Integer> {

    /** Instantanés d'une localité — périmètre par localité, motif habituel des ressources internes (§1). */
    List<SnapshotStats> findByIdLocalite(String idLocalite);

    /** Cet instantané relève-t-il de cette localité ? (garde du détail — 403 sinon). */
    boolean existsByIdSnapshotAndIdLocalite(Integer idSnapshot, String idLocalite);
}
