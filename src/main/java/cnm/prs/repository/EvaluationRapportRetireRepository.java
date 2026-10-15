package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.EvaluationRapportRetire;

public interface EvaluationRapportRetireRepository extends JpaRepository<EvaluationRapportRetire, Long> {

    List<EvaluationRapportRetire> findByIdDmcOrderByRetireLeAscIdAsc(Long idDmc);
}
