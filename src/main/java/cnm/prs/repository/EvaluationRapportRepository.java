package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.EvaluationRapport;

public interface EvaluationRapportRepository extends JpaRepository<EvaluationRapport, Long> {

    /** Les rapports produits et pas encore entièrement signés. */
    List<EvaluationRapport> findBySigneLeIsNull();
}
