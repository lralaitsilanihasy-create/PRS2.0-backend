package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.OffreMorceau;

/** ⚠️ V68 (2026-10-04, lot 3) — les morceaux reçus d'une offre. */
@Repository
public interface OffreMorceauRepository extends JpaRepository<OffreMorceau, Long> {

    List<OffreMorceau> findByIdOffreOrderByRangAsc(String idOffre);

    Optional<OffreMorceau> findByIdOffreAndRang(String idOffre, Integer rang);

    void deleteByIdOffre(String idOffre);
}
