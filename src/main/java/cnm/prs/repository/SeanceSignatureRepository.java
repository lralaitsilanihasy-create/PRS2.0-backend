package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.SeanceSignature;

/** ⚠️ V70 (2026-10-04, arbitrages du pilote, §B2) — les signatures du PV d'ouverture. */
@Repository
public interface SeanceSignatureRepository extends JpaRepository<SeanceSignature, Long> {

    List<SeanceSignature> findByIdDmcOrderByDateAscIdAsc(Long idDmc);

    boolean existsByIdDmcAndIm(Long idDmc, String im);
}
