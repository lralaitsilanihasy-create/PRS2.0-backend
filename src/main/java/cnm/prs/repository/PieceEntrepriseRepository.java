package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.PieceEntreprise;

/** ⚠️ V64 (2026-10-04, soumission en ligne, lot 1b). */
public interface PieceEntrepriseRepository extends JpaRepository<PieceEntreprise, Integer> {

    List<PieceEntreprise> findByIdEntrepriseOrderByIdPieceAsc(Integer idEntreprise);
}
