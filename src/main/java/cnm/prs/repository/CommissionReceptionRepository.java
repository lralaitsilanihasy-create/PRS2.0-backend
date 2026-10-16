package cnm.prs.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.CommissionReception;

/** ⚠️ V127 (lot MS-4) — la commission de réception d'un contrat en vigueur. */
public interface CommissionReceptionRepository extends JpaRepository<CommissionReception, Long> {
}
