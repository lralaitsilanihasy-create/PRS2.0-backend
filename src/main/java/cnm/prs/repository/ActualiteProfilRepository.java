package cnm.prs.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import cnm.prs.entity.ActualiteProfil;

/**
 * Accès au ciblage par profil des actualités ({@code t_actualite_profil}).
 */
public interface ActualiteProfilRepository extends JpaRepository<ActualiteProfil, Integer> {

    List<ActualiteProfil> findByIdActualite(Integer idActualite);

    List<ActualiteProfil> findByIdActualiteIn(Collection<Integer> ids);

    /**
     * Remplacement du ciblage au PUT : DELETE JPQL <strong>immédiat</strong> — un
     * {@code deleteBy...} dérivé serait flushé APRÈS les inserts (ordre Hibernate), violant
     * l'unicité (ID_ACTUALITE, PROFIL) dès qu'un profil est conservé.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ActualiteProfil p where p.idActualite = :idActualite")
    int effacerParActualite(@Param("idActualite") Integer idActualite);
}
