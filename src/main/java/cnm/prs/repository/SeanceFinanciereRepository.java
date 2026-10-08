package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.SeanceFinanciere;

/** ⚠️ V89 — une ligne par ronde : la courante est la dernière. */
public interface SeanceFinanciereRepository extends JpaRepository<SeanceFinanciere, SeanceFinanciere.Cle> {

    Optional<SeanceFinanciere> findFirstByIdDmcOrderByRondeDesc(Long idDmc);

    List<SeanceFinanciere> findByIdDmcOrderByRondeAsc(Long idDmc);

    boolean existsByIdDmc(Long idDmc);
}
