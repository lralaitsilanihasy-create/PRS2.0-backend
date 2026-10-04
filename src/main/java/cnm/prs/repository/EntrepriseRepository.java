package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.Entreprise;

/** ⚠️ V64 (2026-10-04, soumission en ligne, lot 1b). */
public interface EntrepriseRepository extends JpaRepository<Entreprise, Integer> {

    Optional<Entreprise> findByIdCandidat(String idCandidat);

    Optional<Entreprise> findByNif(String nif);

    Optional<Entreprise> findByStat(String stat);

    Optional<Entreprise> findByRcs(String rcs);

    List<Entreprise> findByVerifStatutOrderByDateCreationAscIdEntrepriseAsc(String statut);

    List<Entreprise> findByAdresseNormaliseeAndIdCandidatNot(String adresse, String idCandidat);

    List<Entreprise> findByIdCandidatNot(String idCandidat);
}
