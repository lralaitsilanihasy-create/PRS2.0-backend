package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.ParametreInterneJournal;

/** ⚠️ V50 (2026-09-27, remise électronique, §B4, Q7) — le journal dédié d'une procédure, du plus ancien au plus récent. */
@Repository
public interface ParametreInterneJournalRepository extends JpaRepository<ParametreInterneJournal, Long> {

    List<ParametreInterneJournal> findByIdDmcOrderByDateAscIdAsc(Long idDmc);
}
