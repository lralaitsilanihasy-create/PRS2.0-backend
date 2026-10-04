package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.CodeCandidat;

/** ⚠️ V63 (2026-10-04) — les codes de confirmation des comptes candidats. */
public interface CodeCandidatRepository extends JpaRepository<CodeCandidat, Integer> {

    /** Le dernier code émis, non utilisé, d'un canal. */
    Optional<CodeCandidat> findFirstByIdCandidatAndCanalAndUtiliseFalseOrderByDateEmissionDescIdCodeDesc(String idCandidat,
            String canal);

    List<CodeCandidat> findByIdCandidatAndUtiliseFalse(String idCandidat);
}
