package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.SeanceApport;

/** ⚠️ V69 (2026-10-04, lot 4) — les apports de parts en séance. */
@Repository
public interface SeanceApportRepository extends JpaRepository<SeanceApport, Long> {

    List<SeanceApport> findByIdDmcOrderByDateAscIdAsc(Long idDmc);

    Optional<SeanceApport> findByIdDmcAndDetenteur(Long idDmc, String detenteur);
}
