package cnm.prs.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.EvaluationTechnique;

public interface EvaluationTechniqueRepository extends JpaRepository<EvaluationTechnique, EvaluationTechnique.Cle> {
}
