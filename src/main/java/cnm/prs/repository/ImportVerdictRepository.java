package cnm.prs.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.ImportVerdict;

/** ⚠️ V128 — les verdicts d'identité des DAO lus à l'import. */
@Repository
public interface ImportVerdictRepository extends JpaRepository<ImportVerdict, Long> {

    Optional<ImportVerdict> findByIdDmcAndEmpreinteAndUtilisateur(Long idDmc, String empreinte, String utilisateur);

    /** La purge : une lecture de plus d'un jour ne sert plus. */
    @Modifying
    @Query("delete from ImportVerdict v where v.luLe < :avant")
    int purger(@Param("avant") LocalDateTime avant);
}
