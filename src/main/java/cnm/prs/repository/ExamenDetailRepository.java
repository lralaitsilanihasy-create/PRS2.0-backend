package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.ExamenDetail;

@Repository
public interface ExamenDetailRepository extends JpaRepository<ExamenDetail, Integer> {

    /** Lignes de grille de contrôle d'un examen (pour l'ANNEXE du PV : observations des points non conformes). */
    List<ExamenDetail> findByIdExamen(Integer idExamen);

    /**
     * Purge (⚠️ règle ajoutée §3.3) — supprime les lignes de grille des examens du circuit d'un dossier
     * retiré (via examen → dispatch → réception → dossier). À appeler <strong>avant</strong> les examens.
     */
    @Modifying
    @Query("delete from ExamenDetail ed where ed.idExamen in "
            + "(select e.idExamen from Examen e where e.idDispatch in "
            + "(select di.idDispatch from Dispatch di where di.idReception in "
            + "(select r.idReception from Reception r where r.idDossier = :idDossier)))")
    int deleteParDossier(@Param("idDossier") Integer idDossier);

    /**
     * Statistiques de non-conformité par point de contrôle (§3.2 / §3.7).
     * Renvoie [idPointCtrl, libellé, nb total d'occurrences, nb non conformes].
     */
    @Query("""
            select ed.idPtControle, ed.ptControle.libelPointCtrl, count(ed),
                   sum(case when ed.conforme = false then 1L else 0L end)
            from ExamenDetail ed
            group by ed.idPtControle, ed.ptControle.libelPointCtrl
            """)
    List<Object[]> statsNonConformiteParPoint();

    /** Idem, filtré sur la localité du dossier (examen → dispatch → réception → dossier), §3.3. */
    @Query("""
            select ed.idPtControle, ed.ptControle.libelPointCtrl, count(ed),
                   sum(case when ed.conforme = false then 1L else 0L end)
            from ExamenDetail ed
            where exists (select 1 from Examen e, Dispatch di, Reception r, Dossier d
                          where e.idExamen = ed.idExamen and di.idDispatch = e.idDispatch
                            and r.idReception = di.idReception and d.idDossier = r.idDossier
                            and d.idLocalite = :loc)
            group by ed.idPtControle, ed.ptControle.libelPointCtrl
            """)
    List<Object[]> statsNonConformiteParPointParLocalite(@Param("loc") String loc);
}
