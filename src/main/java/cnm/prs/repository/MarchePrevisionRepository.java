package cnm.prs.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.MarchePrevision;

@Repository
public interface MarchePrevisionRepository extends JpaRepository<MarchePrevision, Integer> {

    /** Dates prévisionnelles d'un marché donné. */
    List<MarchePrevision> findByIdDetail(Integer idDetail);

    /** Dates prévisionnelles d'un ensemble de marchés — scoping de la liste sur le périmètre du parent. */
    List<MarchePrevision> findByIdDetailIn(Collection<Integer> idDetails);

    /** Dates prévisionnelles d'un marché, triées par l'ordre du processus ({@code t_capm.ORDRE}) ASC. */
    @Query("select p from MarchePrevision p left join fetch p.capm c where p.idDetail = :idDetail order by c.ordre asc")
    List<MarchePrevision> findByMarcheOrdonne(@Param("idDetail") Integer idDetail);

    /** Supprime les dates prévisionnelles d'un marché (cascade applicative à la suppression du marché). */
    long deleteByIdDetail(Integer idDetail);

    /** Plus grand ID_PREVISION existant (0 si table vide) — pour allouer la PK assignée à la saisie. */
    @Query("select coalesce(max(p.idPrevision), 0) from MarchePrevision p")
    Integer findMaxId();
}
