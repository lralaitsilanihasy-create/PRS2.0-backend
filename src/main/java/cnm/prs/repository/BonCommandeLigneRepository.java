package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.BonCommandeLigne;

/** ⚠️ V126 (lot MS-3) — les articles d'un bon de commande. */
public interface BonCommandeLigneRepository extends JpaRepository<BonCommandeLigne, Long> {

    List<BonCommandeLigne> findByIdBonOrderByRangAsc(Long idBon);
}
