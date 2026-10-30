package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.ContratVigueur;

/** ⚠️ V124 (lot MS-1) — les contrats-cadres et marchés à commandes en vigueur. */
public interface ContratVigueurRepository extends JpaRepository<ContratVigueur, Long> {

    Optional<ContratVigueur> findByIdDmcAndLot(Long idDmc, Integer lot);

    List<ContratVigueur> findAllByOrderByDateFinAscIdAsc();

    /** ⚠️ 2026-10-30 (« contrat-cadre-4b », §B3) — les contrats des lots d'une procédure. */
    List<ContratVigueur> findByIdDmcOrderByLotAsc(Long idDmc);
}
