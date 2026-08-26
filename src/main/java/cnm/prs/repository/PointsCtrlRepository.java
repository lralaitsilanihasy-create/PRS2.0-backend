package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.PointsCtrl;

@Repository
public interface PointsCtrlRepository extends JpaRepository<PointsCtrl, Integer> {

    /** Points d'une famille (écran admin), spécifiques sous-type compris, triés par ordre. */
    List<PointsCtrl> findByIdTypeDossierOrderByOrdrePointCtrlAsc(String idTypeDossier);

    /**
     * ⚠️ Règle ajoutée — <strong>grille effective</strong> d'un sous-type : points <strong>communs</strong>
     * de la famille ({@code idSousType} null) + points <strong>spécifiques</strong> du sous-type, triés
     * par ordre. C'est la grille servie à l'écran d'examen (grille d'un {@code PPM} ≠ {@code PPM-AGPM}).
     */
    @Query("""
            select p from PointsCtrl p
            where p.idTypeDossier = :famille
              and (p.idSousType is null or p.idSousType = :sousType)
            order by p.ordrePointCtrl asc
            """)
    List<PointsCtrl> findGrilleEffective(@Param("famille") String famille, @Param("sousType") String sousType);


    /** Prochaine PK allouee par la sequence serveur {@code seq_points_ctrl} (allocation atomique). */
    @Query(value = "select nextval('seq_points_ctrl')", nativeQuery = true)
    Long nextIdPointCtrl();
}
