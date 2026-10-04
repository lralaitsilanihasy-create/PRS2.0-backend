package cnm.prs.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.Cao;

/** ⚠️ V67 (2026-10-04, soumission en ligne, lot 2a) — la commission d'appel d'offres d'un DAO. */
@Repository
public interface CaoRepository extends JpaRepository<Cao, Long> {
}
