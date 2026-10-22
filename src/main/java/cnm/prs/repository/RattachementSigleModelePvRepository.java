package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.RattachementSigleModelePv;

/** ⚠️ 2026-10-22 (« modeles-dao », D1-D3 ; V141). */
public interface RattachementSigleModelePvRepository
        extends JpaRepository<RattachementSigleModelePv, RattachementSigleModelePv.Cle> {

    List<RattachementSigleModelePv> findByIdModele(Long idModele);

    Optional<RattachementSigleModelePv> findBySigleAndNature(String sigle, String nature);
}
