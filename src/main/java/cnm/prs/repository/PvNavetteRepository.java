package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.PvNavette;

@Repository
public interface PvNavetteRepository extends JpaRepository<PvNavette, Integer> {

    /**
     * Prochaine PK de navette, allouée par la séquence serveur (Voie B — l'id client est ignoré).
     * Remplace un {@code max(ID_NAVETTE) + 1}, lu par deux mouvements simultanés à l'identique.
     *
     * <p>À ne pas confondre avec {@link #findMaxNumNavetteByPv} : {@code NUM_NAVETTE} est le rang
     * MÉTIER du mouvement dans SON PV (1, 2, 3…), affiché à l'utilisateur et repris dans NB_NAVETTES.
     * Il reste calculé par {@code max + 1} sur le PV concerné — une séquence globale le rendrait faux.
     */
    @Query(value = "select nextval('seq_pv_navette')", nativeQuery = true)
    Long nextIdNavette();

    /**
     * Purge (⚠️ règle ajoutée §3.3) — supprime les navettes des PV du circuit d'un dossier retiré
     * (via PV → examen → dispatch → réception → dossier). À appeler <strong>avant</strong> les PV.
     */
    @Modifying
    @Query("delete from PvNavette n where n.idPv in "
            + "(select pv.idPv from PvExamen pv where pv.idExamen in "
            + "(select e.idExamen from Examen e where e.idDispatch in "
            + "(select di.idDispatch from Dispatch di where di.idReception in "
            + "(select r.idReception from Reception r where r.idDossier = :idDossier))))")
    int deleteParDossier(@Param("idDossier") Integer idDossier);

    /** Plus grand NUM_NAVETTE pour un PV donné (0 si aucune navette) — pour incrémenter. */
    @Query("select coalesce(max(n.numNavette), 0) from PvNavette n where n.idPv = :idPv")
    Integer findMaxNumNavetteByPv(@Param("idPv") Integer idPv);

    /**
     * Navettes d'une localité — la navette n'a pas de périmètre propre : elle hérite de celui de son
     * PV, lui-même rattaché à la localité du contrôleur réceptionnaire
     * (PV → examen → dispatch → réception → contrôleur), exactement comme {@code PvExamenRepository}.
     */
    @Query("select n from PvNavette n where n.pv.examen.dispatch.reception.ctrlRecept.idLocalite = :loc")
    List<PvNavette> findVisiblesParLocalite(@Param("loc") String loc);

    /** Cette navette relève-t-elle de cette localité ? (garde du détail — 403 sinon). */
    @Query("select (count(n) > 0) from PvNavette n where n.idNavette = :id "
            + "and n.pv.examen.dispatch.reception.ctrlRecept.idLocalite = :loc")
    boolean existsDansLocalite(@Param("id") Integer id, @Param("loc") String loc);
}
