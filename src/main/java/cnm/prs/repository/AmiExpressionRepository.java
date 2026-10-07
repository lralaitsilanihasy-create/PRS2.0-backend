package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.AmiExpression;

public interface AmiExpressionRepository extends JpaRepository<AmiExpression, String> {

    List<AmiExpression> findByIdDmcAndEtatOrderByNumeroAsc(Long idDmc, String etat);

    Optional<AmiExpression> findFirstByIdDmcAndIdCandidatAndEtat(Long idDmc, String idCandidat, String etat);

    long countByIdDmcAndEtat(Long idDmc, String etat);

    long countByIdDmc(Long idDmc);
}
