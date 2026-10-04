package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.RetraitDao;

/** ⚠️ V65 (2026-10-04, soumission en ligne, lot 1c) — le registre des retraits du DAO. */
public interface RetraitDaoRepository extends JpaRepository<RetraitDao, Integer> {

    List<RetraitDao> findByIdDmcOrderByDateRetraitAscIdRetraitAsc(Long idDmc);
}
