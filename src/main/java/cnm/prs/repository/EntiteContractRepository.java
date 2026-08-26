package cnm.prs.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.EntiteContract;

@Repository
public interface EntiteContractRepository extends JpaRepository<EntiteContract, Integer> {

    /** Prochaine PK allouee par la sequence serveur {@code seq_entite_contract} (allocation atomique). */
    @Query(value = "select nextval('seq_entite_contract')", nativeQuery = true)
    Long nextIdEntiteContract();
}
