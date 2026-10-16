package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.ContratVigueur;

/** ⚠️ V124 (lot MS-1) — les contrats-cadres et marchés à commandes en vigueur. */
public interface ContratVigueurRepository extends JpaRepository<ContratVigueur, Long> {

    Optional<ContratVigueur> findByIdDmcAndLot(Long idDmc, Integer lot);

    List<ContratVigueur> findAllByOrderByDateFinAscIdAsc();
}
