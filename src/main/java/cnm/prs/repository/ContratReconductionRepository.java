package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.ContratReconduction;

/** ⚠️ V124 (lot MS-1) — les décisions de reconduction d'un contrat-cadre. */
public interface ContratReconductionRepository extends JpaRepository<ContratReconduction, Long> {

    List<ContratReconduction> findByIdContratOrderByLeAscIdAsc(Long idContrat);
}
