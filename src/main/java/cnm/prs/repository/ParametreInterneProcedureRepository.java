package cnm.prs.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.ParametreInterneProcedure;

/** ⚠️ V50 (2026-09-27, remise électronique, §B4) — une ligne par DMC, clé {@code ID_DMC}. */
@Repository
public interface ParametreInterneProcedureRepository extends JpaRepository<ParametreInterneProcedure, Long> {
}
