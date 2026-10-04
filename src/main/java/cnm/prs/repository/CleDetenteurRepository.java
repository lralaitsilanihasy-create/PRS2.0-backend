package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.CleDetenteur;

/** ⚠️ V66 (2026-10-04, soumission en ligne, lot 2) — les clés des détenteurs de part. */
@Repository
public interface CleDetenteurRepository extends JpaRepository<CleDetenteur, Long> {

    /** Les clés actives (non archivées) d'une procédure. */
    List<CleDetenteur> findByIdDmcAndDateArchivageIsNullOrderByIdCleAsc(Long idDmc);

    /** La clé active d'un membre. */
    Optional<CleDetenteur> findFirstByIdDmcAndRoleAndImAndDateArchivageIsNull(Long idDmc, String role, String im);

    /** La clé active de la part de secours. */
    Optional<CleDetenteur> findFirstByIdDmcAndRoleAndDateArchivageIsNull(Long idDmc, String role);
}
