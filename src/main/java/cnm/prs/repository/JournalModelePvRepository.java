package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.JournalModelePv;

/** ⚠️ 2026-10-20 (« generateur-modeles-pv », lot G1 ; V138). */
public interface JournalModelePvRepository extends JpaRepository<JournalModelePv, Long> {

    List<JournalModelePv> findByIdModeleOrderByLeDescIdJournalDesc(Long idModele);
}
