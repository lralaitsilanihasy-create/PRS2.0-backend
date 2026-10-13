package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.QuestionCandidat;

/** ⚠️ R7 (manuel révisé, lot 2, §B2 ; V109) — les questions des candidats d'une procédure en ligne. */
public interface QuestionCandidatRepository extends JpaRepository<QuestionCandidat, Long> {

    List<QuestionCandidat> findByIdDmcOrderByPoseeLeAscIdAsc(Long idDmc);

    List<QuestionCandidat> findByIdDmcAndIdCandidatOrderByPoseeLeAscIdAsc(Long idDmc, String idCandidat);

    List<QuestionCandidat> findByIdDmcAndPublieeLeIsNotNullOrderByPoseeLeAscIdAsc(Long idDmc);

    long countByIdDmcAndRepondueLeIsNull(Long idDmc);
}
