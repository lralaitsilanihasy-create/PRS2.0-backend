package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.AttributionExplication;

public interface AttributionExplicationRepository extends JpaRepository<AttributionExplication, Long> {

    List<AttributionExplication> findByIdDmcAndLotOrderByDemandeeLeAscIdAsc(Long idDmc, Integer lot);

    List<AttributionExplication> findByIdOffreOrderByDemandeeLeAscIdAsc(String idOffre);
}
