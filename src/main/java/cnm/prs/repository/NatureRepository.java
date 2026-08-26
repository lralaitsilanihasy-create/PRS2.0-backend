package cnm.prs.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.Nature;

@Repository
public interface NatureRepository extends JpaRepository<Nature, Integer> {

    /** Prochaine PK allouee par la sequence serveur {@code seq_nature} (allocation atomique). */
    @Query(value = "select nextval('seq_nature')", nativeQuery = true)
    Long nextIdNature();
}
