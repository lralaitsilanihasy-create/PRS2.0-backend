package cnm.prs.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.DefiCle;

/** ⚠️ V66 (2026-10-04, soumission en ligne, lot 2, S2) — les défis de vérification d'une part. */
@Repository
public interface DefiCleRepository extends JpaRepository<DefiCle, Long> {
}
