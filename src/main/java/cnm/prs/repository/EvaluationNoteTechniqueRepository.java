package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.EvaluationNoteTechnique;

public interface EvaluationNoteTechniqueRepository extends JpaRepository<EvaluationNoteTechnique, Long> {

    List<EvaluationNoteTechnique> findByIdDmcOrderByIdAsc(Long idDmc);

    Optional<EvaluationNoteTechnique> findByIdOffreAndImAndElement(String idOffre, String im, String element);
}
