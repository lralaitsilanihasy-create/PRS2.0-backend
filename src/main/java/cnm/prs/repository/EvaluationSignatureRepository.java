package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.EvaluationSignature;

public interface EvaluationSignatureRepository extends JpaRepository<EvaluationSignature, Long> {

    List<EvaluationSignature> findByIdDmcOrderByDateAscIdAsc(Long idDmc);

    boolean existsByIdDmcAndIm(Long idDmc, String im);
}
