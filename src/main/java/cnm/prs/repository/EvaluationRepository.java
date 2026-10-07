package cnm.prs.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.Evaluation;

public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {
}
