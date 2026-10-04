package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.CaoMembre;

/** ⚠️ V67 (2026-10-04, soumission en ligne, lot 2a) — les membres des commissions d'appel d'offres. */
@Repository
public interface CaoMembreRepository extends JpaRepository<CaoMembre, Long> {

    List<CaoMembre> findByIdDmcOrderByRangAscIdMembreAsc(Long idDmc);

    /** Les CAO où siège un compte (qualité {@code MEMBRE}). */
    List<CaoMembre> findByIdCompteOrderByIdDmcDesc(String idCompte);
}
