package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.CeremonieCles;

/** ⚠️ V66 (2026-10-04, soumission en ligne, lot 2) — la cérémonie des clés d'une procédure. */
@Repository
public interface CeremonieClesRepository extends JpaRepository<CeremonieCles, Long> {

    List<CeremonieCles> findByEtat(String etat);
}
