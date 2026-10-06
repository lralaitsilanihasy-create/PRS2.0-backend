package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.RecuJournal;

/** ⚠️ V72 — le journal des reçus de frais de dossier. */
@Repository
public interface RecuJournalRepository extends JpaRepository<RecuJournal, Long> {

    List<RecuJournal> findByIdDmcOrderByDateAscIdAsc(Long idDmc);
}
