package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.EvaluationDeclaration;

public interface EvaluationDeclarationRepository extends JpaRepository<EvaluationDeclaration, Long> {

    List<EvaluationDeclaration> findByIdDmcOrderBySigneeLeAscIdAsc(Long idDmc);

    Optional<EvaluationDeclaration> findByIdDmcAndIm(Long idDmc, String im);
}
