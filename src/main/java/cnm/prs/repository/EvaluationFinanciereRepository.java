package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.EvaluationFinanciere;

public interface EvaluationFinanciereRepository extends JpaRepository<EvaluationFinanciere, Long> {

    Optional<EvaluationFinanciere> findByIdOffre(String idOffre);

    List<EvaluationFinanciere> findByIdDmc(Long idDmc);
}
