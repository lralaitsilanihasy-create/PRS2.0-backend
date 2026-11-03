package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.ConsentementAnticipation;

public interface ConsentementAnticipationRepository extends JpaRepository<ConsentementAnticipation, Long> {

    List<ConsentementAnticipation> findByIdDmcOrderByIdAsc(Long idDmc);

    Optional<ConsentementAnticipation> findByIdDmcAndIdCandidat(Long idDmc, String idCandidat);
}
