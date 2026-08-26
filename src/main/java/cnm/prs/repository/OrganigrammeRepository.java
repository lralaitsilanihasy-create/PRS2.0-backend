package cnm.prs.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.Organigramme;

@Repository
public interface OrganigrammeRepository extends JpaRepository<Organigramme, Integer> {

    /** Prochaine PK allouee par la sequence serveur {@code seq_organigramme} (allocation atomique). */
    @Query(value = "select nextval('seq_organigramme')", nativeQuery = true)
    Long nextIdOrganigramme();
}
