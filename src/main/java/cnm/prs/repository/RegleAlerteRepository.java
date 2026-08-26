package cnm.prs.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.RegleAlerte;

@Repository
public interface RegleAlerteRepository extends JpaRepository<RegleAlerte, Integer> {

    /** Prochaine PK allouee par la sequence serveur {@code seq_regle_alerte} (allocation atomique). */
    @Query(value = "select nextval('seq_regle_alerte')", nativeQuery = true)
    Long nextIdRegleAlerte();
}
