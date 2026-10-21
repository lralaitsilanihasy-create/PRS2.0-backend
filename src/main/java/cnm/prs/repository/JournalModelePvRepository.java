package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import cnm.prs.entity.JournalModelePv;

/** ⚠️ 2026-10-20 (« generateur-modeles-pv », lot G1 ; V138). */
public interface JournalModelePvRepository extends JpaRepository<JournalModelePv, Long> {

    List<JournalModelePv> findByIdModeleOrderByLeDescIdJournalDesc(Long idModele);

    /** ⚠️ 2026-10-21 (« modele-pv-suppression », Q1) — le plus grand numéro de version cité au journal, même supprimée. */
    @Query("select max(j.numeroVersion) from JournalModelePv j where j.idModele = :idModele")
    Integer numeroMax(Long idModele);

    boolean existsByIdModeleAndAction(Long idModele, String action);

    void deleteByIdModele(Long idModele);
}
