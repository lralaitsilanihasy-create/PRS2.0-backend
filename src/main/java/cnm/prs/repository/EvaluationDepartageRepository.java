package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.EvaluationDepartage;

public interface EvaluationDepartageRepository extends JpaRepository<EvaluationDepartage, Long> {

    /** Les départages en vigueur (non remplacés) de la procédure. */
    List<EvaluationDepartage> findByIdDmcAndRemplaceLeIsNull(Long idDmc);
}
