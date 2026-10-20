package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.ConditionModelePv;

/** ⚠️ 2026-10-20 (« generateur-modeles-pv », lot G1 ; V138). */
public interface ConditionModelePvRepository extends JpaRepository<ConditionModelePv, Long> {

    List<ConditionModelePv> findByIdVersionOrderByOrdreAsc(Long idVersion);

    void deleteByIdVersion(Long idVersion);
}
