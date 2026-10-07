package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.Attribution;

public interface AttributionRepository extends JpaRepository<Attribution, Attribution.Cle> {

    List<Attribution> findByIdDmcOrderByLotAsc(Long idDmc);

    /** ⚠️ 2c — les lots attribués à une offre (son candidat lit ses pièces, son marché). */
    List<Attribution> findByIdOffreAttribuee(String idOffreAttribuee);
}
