package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.RattachementModelePv;

/** ⚠️ 2026-10-20 (« generateur-modeles-pv », lot G1 ; V138). */
public interface RattachementModelePvRepository extends JpaRepository<RattachementModelePv, RattachementModelePv.Cle> {

    List<RattachementModelePv> findByIdModele(Long idModele);

    List<RattachementModelePv> findByNature(String nature);

    Optional<RattachementModelePv> findByIdSousTypeAndNature(String idSousType, String nature);
}
