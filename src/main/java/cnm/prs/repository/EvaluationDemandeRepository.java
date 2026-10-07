package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.EvaluationDemande;

public interface EvaluationDemandeRepository extends JpaRepository<EvaluationDemande, Long> {

    List<EvaluationDemande> findByIdOffreAndTypeOrderByDemandeeLeAscIdAsc(String idOffre, String type);

    List<EvaluationDemande> findByIdDmcOrderByDemandeeLeAscIdAsc(Long idDmc);
}
