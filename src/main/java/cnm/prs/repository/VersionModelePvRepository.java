package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.VersionModelePv;

/** ⚠️ 2026-10-20 (« generateur-modeles-pv », lot G1 ; V138). */
public interface VersionModelePvRepository extends JpaRepository<VersionModelePv, Long> {

    List<VersionModelePv> findByIdModeleOrderByNumeroDesc(Long idModele);

    Optional<VersionModelePv> findByIdModeleAndNumero(Long idModele, Integer numero);

    Optional<VersionModelePv> findByIdModeleAndEtat(Long idModele, String etat);
}
