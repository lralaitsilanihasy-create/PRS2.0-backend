package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.SeanceFinanciereSignature;

/** ⚠️ R1 (manuel révisé, lot 1 ; V101) — signatures du PV de la seconde séance, par ronde. */
@Repository
public interface SeanceFinanciereSignatureRepository extends JpaRepository<SeanceFinanciereSignature, Long> {

    List<SeanceFinanciereSignature> findByIdDmcAndRondeOrderByDateAscIdAsc(Long idDmc, Integer ronde);

    boolean existsByIdDmcAndRondeAndIm(Long idDmc, Integer ronde, String im);
}
