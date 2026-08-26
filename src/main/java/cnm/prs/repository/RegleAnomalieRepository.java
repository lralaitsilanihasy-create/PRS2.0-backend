package cnm.prs.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.RegleAnomalie;

@Repository
public interface RegleAnomalieRepository extends JpaRepository<RegleAnomalie, Integer> {

    /** Prochaine PK allouee par la sequence serveur {@code seq_regle_anomalie} (allocation atomique). */
    @Query(value = "select nextval('seq_regle_anomalie')", nativeQuery = true)
    Long nextIdRegleAnomalie();
}
