package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.EvaluationJournal;

public interface EvaluationJournalRepository extends JpaRepository<EvaluationJournal, Long> {

    List<EvaluationJournal> findByIdDmcOrderByDateAscIdAsc(Long idDmc);
}
