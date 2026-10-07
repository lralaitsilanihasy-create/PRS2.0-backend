package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.AttributionRecours;

public interface AttributionRecoursRepository extends JpaRepository<AttributionRecours, Long> {

    List<AttributionRecours> findByIdDmcAndLotOrderByDateReceptionAscIdAsc(Long idDmc, Integer lot);
}
