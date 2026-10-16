package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.CommissionReceptionMembre;

/** ⚠️ V127 (lot MS-4) — les membres d'une commission de réception. */
public interface CommissionReceptionMembreRepository extends JpaRepository<CommissionReceptionMembre, Long> {

    List<CommissionReceptionMembre> findByIdContratOrderByRangAscIdAsc(Long idContrat);

    List<CommissionReceptionMembre> findByIdCompte(String idCompte);
}
