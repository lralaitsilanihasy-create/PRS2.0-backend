package cnm.prs.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.CompteCao;

/** ⚠️ V67 (2026-10-04, soumission en ligne, lot 2a) — les comptes des membres de CAO. */
@Repository
public interface CompteCaoRepository extends JpaRepository<CompteCao, String> {

    Optional<CompteCao> findByEmail(String email);

    @Query(value = "select nextval('public.seq_compte_cao')", nativeQuery = true)
    long prochainNumero();
}
