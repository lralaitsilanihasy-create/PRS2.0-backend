package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.OffreJournal;

/** ⚠️ V68 (2026-10-04, lot 3) — le journal des offres. */
@Repository
public interface OffreJournalRepository extends JpaRepository<OffreJournal, Long> {

    List<OffreJournal> findByIdOffreOrderByDateAscIdAsc(String idOffre);
}
