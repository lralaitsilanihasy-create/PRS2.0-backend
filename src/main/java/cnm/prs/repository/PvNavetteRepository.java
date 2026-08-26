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

    /** Plus grand ID_NAVETTE existant (0 si table vide) — pour générer la PK assignée. */
    @Query("select coalesce(max(n.idNavette), 0) from PvNavette n")
    Integer findMaxIdNavette();

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
     * ⚠️ LOT 3a (2026-08-26) — §1/§3.5 : navettes de la localité de l'appelant. La navette est une
     * pièce <strong>interne</strong> du circuit : sa localité est celle du contrôleur qui a
     * réceptionné le dossier, atteinte par PV → examen → dispatch → réception (même chaîne que
     * {@link #deleteParDossier}). La PRMP n'y a pas accès (elle reçoit la synthèse via le PV).
     */
    @Query("""
            select n from PvNavette n, PvExamen pv, Examen e, Dispatch di, Reception r
            where pv.idPv = n.idPv and e.idExamen = pv.idExamen and di.idDispatch = e.idDispatch
              and r.idReception = di.idReception and r.ctrlRecept.idLocalite = :localite
            """)
    List<PvNavette> findParLocalite(@Param("localite") String localite);

    /** Vrai si la navette relève de la localité donnée (miroir unitaire de {@link #findParLocalite}). */
    @Query("""
            select (count(n) > 0) from PvNavette n, PvExamen pv, Examen e, Dispatch di, Reception r
            where n.idNavette = :id and pv.idPv = n.idPv and e.idExamen = pv.idExamen
              and di.idDispatch = e.idDispatch and r.idReception = di.idReception
              and r.ctrlRecept.idLocalite = :localite
            """)
    boolean existsDansLocalite(@Param("id") Integer id, @Param("localite") String localite);
}
