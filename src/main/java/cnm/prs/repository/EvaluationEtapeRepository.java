package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.EvaluationEtape;

public interface EvaluationEtapeRepository extends JpaRepository<EvaluationEtape, Long> {

    /** Les arrêts en vigueur (non rouverts) de la procédure. */
    List<EvaluationEtape> findByIdDmcAndRouverteLeIsNullOrderByArreteeLeAscIdAsc(Long idDmc);
}
