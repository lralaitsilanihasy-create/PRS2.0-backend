package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.RapprochementCandidat;

/** ⚠️ V64 (2026-10-04, soumission en ligne, lot 1b). */
public interface RapprochementCandidatRepository extends JpaRepository<RapprochementCandidat, Integer> {

    @Modifying
    @Query("delete from RapprochementCandidat r where r.idCandidatA = :id or r.idCandidatB = :id")
    void supprimerPour(@Param("id") String idCandidat);

    @Query("select r from RapprochementCandidat r where r.idCandidatA = :id or r.idCandidatB = :id order by r.critere, r.idRapprochement")
    List<RapprochementCandidat> pour(@Param("id") String idCandidat);
}
