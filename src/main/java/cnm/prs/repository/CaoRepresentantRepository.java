package cnm.prs.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.CaoRepresentant;

/** ⚠️ R1 (manuel révisé, lot 1 ; V101) — le représentant du président de la CAO, par contexte. */
@Repository
public interface CaoRepresentantRepository extends JpaRepository<CaoRepresentant, Long> {

    Optional<CaoRepresentant> findByIdDmcAndContexte(Long idDmc, String contexte);
}
