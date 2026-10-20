package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.ModelePv;

/** ⚠️ 2026-10-20 (« generateur-modeles-pv », lot G1 ; V138). */
public interface ModelePvRepository extends JpaRepository<ModelePv, Long> {

    Optional<ModelePv> findByCode(String code);

    List<ModelePv> findAllByOrderByLibelleAsc();
}
