package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.AttributionRecours;

public interface AttributionRecoursRepository extends JpaRepository<AttributionRecours, Long> {

    /** Les recours du cycle en cours du lot (⚠️ V92 : ceux d'un cycle retiré sont archivés). */
    @org.springframework.data.jpa.repository.Query("select r from AttributionRecours r where r.idDmc = :idDmc and r.lot = :lot and r.archiveLe is null "
            + "order by r.dateReception asc, r.id asc")
    List<AttributionRecours> findByIdDmcAndLotOrderByDateReceptionAscIdAsc(@org.springframework.data.repository.query.Param("idDmc") Long idDmc,
            @org.springframework.data.repository.query.Param("lot") Integer lot);

    /** ⚠️ 2d-1 (§B7) — les recours d'un type sans décision, dont l'échéance n'a pas encore été signalée. */
    List<AttributionRecours> findByTypeAndDecideLeIsNullAndAlerteLeIsNullAndArchiveLeIsNull(String type);
}
