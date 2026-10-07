package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.EvaluationDecision;

public interface EvaluationDecisionRepository extends JpaRepository<EvaluationDecision, Long> {

    /** Les décisions en vigueur (non remplacées) de la procédure. */
    List<EvaluationDecision> findByIdDmcAndRemplaceeLeIsNullOrderByLeAscIdAsc(Long idDmc);

    List<EvaluationDecision> findByIdOffreAndEtapeAndRemplaceeLeIsNull(String idOffre, String etape);
}
