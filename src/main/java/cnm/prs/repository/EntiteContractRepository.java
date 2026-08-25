package cnm.prs.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.EntiteContract;

@Repository
public interface EntiteContractRepository extends JpaRepository<EntiteContract, Integer> {

    /**
     * Prochaine PK d'entité contractante, allouée par la séquence serveur (Voie B), à la création
     * d'une entité proposée lors de la validation d'une inscription.
     *
     * <p>Remplace un {@code max(ID_ENTITE_CONTRACT) + 1}. À consommer <strong>une fois par ligne</strong> :
     * une validation peut accepter plusieurs entités proposées d'affilée.
     */
    @Query(value = "select nextval('seq_entite_contract')", nativeQuery = true)
    Long nextIdEntiteContract();
}
