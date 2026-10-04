package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.EntrepriseJournal;

/** ⚠️ V64 (2026-10-04, soumission en ligne, lot 1b). */
public interface EntrepriseJournalRepository extends JpaRepository<EntrepriseJournal, Integer> {

    List<EntrepriseJournal> findByIdEntrepriseOrderByDateActionAscIdJournalAsc(Integer idEntreprise);
}
