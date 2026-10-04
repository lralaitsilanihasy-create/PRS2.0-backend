package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.SeanceJournal;

/** ⚠️ V69 (2026-10-04, lot 4) — le journal de la séance. */
@Repository
public interface SeanceJournalRepository extends JpaRepository<SeanceJournal, Long> {

    List<SeanceJournal> findByIdDmcOrderByDateAscIdAsc(Long idDmc);
}
