package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.ExclusionArmpJournal;

/** ⚠️ V64 (2026-10-04, soumission en ligne, lot 1b). */
public interface ExclusionArmpJournalRepository extends JpaRepository<ExclusionArmpJournal, Integer> {

    List<ExclusionArmpJournal> findByIdExclusionOrderByDateActionAscIdJournalAsc(Integer idExclusion);
}
