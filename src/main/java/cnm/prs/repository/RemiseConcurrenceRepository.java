package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.RemiseConcurrence;

/** ⚠️ V125 (lot MS-2) — les remises en concurrence d'un contrat-cadre en vigueur. */
public interface RemiseConcurrenceRepository extends JpaRepository<RemiseConcurrence, Long> {

    List<RemiseConcurrence> findByIdContratOrderByNumeroAsc(Long idContrat);

    Optional<RemiseConcurrence> findFirstByIdContratOrderByNumeroDesc(Long idContrat);
}
