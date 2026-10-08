package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.AttributionRecours;

public interface AttributionRecoursRepository extends JpaRepository<AttributionRecours, Long> {

    List<AttributionRecours> findByIdDmcAndLotOrderByDateReceptionAscIdAsc(Long idDmc, Integer lot);

    /** ⚠️ 2d-1 (§B7) — les recours d'un type sans décision, dont l'échéance n'a pas encore été signalée. */
    List<AttributionRecours> findByTypeAndDecideLeIsNullAndAlerteLeIsNull(String type);
}
