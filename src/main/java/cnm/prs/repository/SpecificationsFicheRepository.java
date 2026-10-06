package cnm.prs.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.SpecificationsFiche;

/** ⚠️ V73 — les spécifications techniques d'une version de fiche (clé : {@code ID_FICHE}). */
@Repository
public interface SpecificationsFicheRepository extends JpaRepository<SpecificationsFiche, Integer> {
}
