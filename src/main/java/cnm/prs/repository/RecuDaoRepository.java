package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.RecuDao;

/** ⚠️ V72 — les reçus de frais de dossier. */
@Repository
public interface RecuDaoRepository extends JpaRepository<RecuDao, Integer> {

    List<RecuDao> findByIdDmcOrderByDateDepotDescIdRecuDesc(Long idDmc);

    List<RecuDao> findByIdDmcAndNifOrderByDateDepotDescIdRecuDesc(Long idDmc, String nif);
}
