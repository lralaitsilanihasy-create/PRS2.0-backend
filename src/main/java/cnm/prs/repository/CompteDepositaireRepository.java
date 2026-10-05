package cnm.prs.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.CompteDepositaire;

/** ⚠️ V71 (2026-10-05, dépositaire de la part de secours) — les comptes des dépositaires. */
@Repository
public interface CompteDepositaireRepository extends JpaRepository<CompteDepositaire, String> {

    Optional<CompteDepositaire> findByEmail(String email);

    @Query(value = "select nextval('public.seq_compte_depositaire')", nativeQuery = true)
    long prochainNumero();
}
