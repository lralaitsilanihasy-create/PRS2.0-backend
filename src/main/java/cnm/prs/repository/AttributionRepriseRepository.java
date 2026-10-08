package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.AttributionReprise;

public interface AttributionRepriseRepository extends JpaRepository<AttributionReprise, Long> {

    List<AttributionReprise> findByIdDmcAndLotOrderByIdAsc(Long idDmc, Integer lot);
}
