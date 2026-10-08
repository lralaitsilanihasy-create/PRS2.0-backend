package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import cnm.prs.entity.AttributionLettre;

public interface AttributionLettreRepository extends JpaRepository<AttributionLettre, Long> {

    /** Les lettres du cycle en cours du lot (⚠️ V92 : celles d'un cycle retiré sont archivées). */
    @Query("select x from AttributionLettre x where x.idDmc = :idDmc and x.lot = :lot and x.archiveLe is null order by x.id asc")
    List<AttributionLettre> findByIdDmcAndLotOrderByIdAsc(@Param("idDmc") Long idDmc, @Param("lot") Integer lot);

    /** Les lettres du cycle en cours d'une offre, la plus récente d'abord. */
    @Query("select x from AttributionLettre x where x.idOffre = :idOffre and x.archiveLe is null order by x.id desc")
    List<AttributionLettre> findByIdOffreOrderByIdDesc(@Param("idOffre") String idOffre);
}
