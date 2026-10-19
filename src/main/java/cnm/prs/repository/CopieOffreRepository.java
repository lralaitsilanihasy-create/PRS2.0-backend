package cnm.prs.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.CopieOffre;

/** ⚠️ V136 (2026-10-19, « copie-offre-candidat ») — les copies chiffrées des offres, gardées pour leur seul candidat. */
@Repository
public interface CopieOffreRepository extends JpaRepository<CopieOffre, String> {

    List<CopieOffre> findByIdOffreIn(Collection<String> idOffres);

    /** Les copies encore lisibles des offres données (l'effacement à la clôture de la séance). */
    @Query("select c from CopieOffre c where c.idOffre in :idOffres and c.effaceeLe is null")
    List<CopieOffre> lisibles(@Param("idOffres") Collection<String> idOffres);
}
