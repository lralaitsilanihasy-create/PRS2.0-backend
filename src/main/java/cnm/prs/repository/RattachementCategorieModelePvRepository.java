package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.RattachementCategorieModelePv;

/** ⚠️ 2026-10-22 (« modeles-documents-marche », lot M1 ; V140). */
public interface RattachementCategorieModelePvRepository
        extends JpaRepository<RattachementCategorieModelePv, RattachementCategorieModelePv.Cle> {

    List<RattachementCategorieModelePv> findByIdModele(Long idModele);

    Optional<RattachementCategorieModelePv> findByCategorieAndNature(String categorie, String nature);
}
