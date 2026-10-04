package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.ExclusionArmp;

/** ⚠️ V64 (2026-10-04, soumission en ligne, lot 1b). */
public interface ExclusionArmpRepository extends JpaRepository<ExclusionArmp, Integer> {

    List<ExclusionArmp> findByNifOrderByDateDebutDesc(String nif);

    List<ExclusionArmp> findAllByOrderByDateDebutDescIdExclusionDesc();
}
