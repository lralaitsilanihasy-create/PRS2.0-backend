package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.AttributionReprise;

public interface AttributionRepriseRepository extends JpaRepository<AttributionReprise, Long> {

    List<AttributionReprise> findByIdDmcAndLotOrderByIdAsc(Long idDmc, Integer lot);

    /** ⚠️ R9c (14/10) — une reprise ou réattribution de la procédure postérieure à cette date lève le verrou des étapes closes. */
    boolean existsByIdDmcAndLeAfter(Long idDmc, java.time.LocalDateTime le);
}
