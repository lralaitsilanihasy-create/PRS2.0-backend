package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.Attribution;

public interface AttributionRepository extends JpaRepository<Attribution, Attribution.Cle> {

    List<Attribution> findByIdDmcOrderByLotAsc(Long idDmc);
}
