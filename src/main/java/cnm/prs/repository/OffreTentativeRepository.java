package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.OffreTentative;

/** ⚠️ V112 (2026-10-14, R9b) — les tentatives de dépôt refusées hors délai. */
@Repository
public interface OffreTentativeRepository extends JpaRepository<OffreTentative, Long> {

    List<OffreTentative> findByIdDmcOrderByHorodatageAscIdAsc(Long idDmc);

    boolean existsByIdOffreAndGeste(String idOffre, String geste);
}
