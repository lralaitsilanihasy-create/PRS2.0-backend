package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.ActionDossier;

@Repository
public interface ActionDossierRepository extends JpaRepository<ActionDossier, Long> {

    /** Journal d'un dossier, du plus ancien au plus récent. */
    List<ActionDossier> findByIdDossierOrderByDateActionAscIdActionAsc(Integer idDossier);

    /** Purge liée à la suppression d'un brouillon (le dossier disparaît, son journal aussi). */
    void deleteByIdDossier(Integer idDossier);
}
