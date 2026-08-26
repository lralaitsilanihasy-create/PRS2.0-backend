package cnm.prs.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.Ministere;

@Repository
public interface MinistereRepository extends JpaRepository<Ministere, Integer> {

    /** Prochaine PK allouee par la sequence serveur {@code seq_ministere} (allocation atomique). */
    @Query(value = "select nextval('seq_ministere')", nativeQuery = true)
    Long nextIdMinistere();
}
