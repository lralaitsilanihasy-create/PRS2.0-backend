package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.CopieDossier;

@Repository
public interface CopieDossierRepository extends JpaRepository<CopieDossier, Integer> {

    /**
     * Purge (⚠️ règle ajoutée §3.3) — supprime les copies de dossier d'un dossier retiré (FK directe
     * {@code ID_DOSSIER}). À appeler <strong>avant</strong> les dispatchs (FK {@code ID_DISPATCH}).
     */
    @Modifying
    @Query("delete from CopieDossier c where c.idDossier = :idDossier")
    int deleteParDossier(@Param("idDossier") Integer idDossier);

    /**
     * Copies d'une localité — la copie de dossier est une pièce du circuit interne (§3.3,
     * {@code TYPE_COPIE = DISPATCH_CC}) : son périmètre est celui de son dossier
     * ({@code t_dossier.ID_LOCALITE}, dérivé de l'entité contractante).
     */
    @Query("select c from CopieDossier c where c.dossier.idLocalite = :loc")
    List<CopieDossier> findVisiblesParLocalite(@Param("loc") String loc);

    /** Cette copie relève-t-elle de cette localité ? (garde du détail — 403 sinon). */
    @Query("select (count(c) > 0) from CopieDossier c where c.idCopie = :id and c.dossier.idLocalite = :loc")
    boolean existsDansLocalite(@Param("id") Integer id, @Param("loc") String loc);
}
