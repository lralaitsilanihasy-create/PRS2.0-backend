package cnm.prs.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.Seance;

/** ⚠️ V69 (2026-10-04, soumission en ligne, lot 4) — la séance d'ouverture des plis. */
@Repository
public interface SeanceRepository extends JpaRepository<Seance, Long> {
}
