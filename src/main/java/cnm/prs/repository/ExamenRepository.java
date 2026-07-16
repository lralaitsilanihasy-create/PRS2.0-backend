package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.Examen;

@Repository
public interface ExamenRepository extends JpaRepository<Examen, Integer> {

    /**
     * Purge (⚠️ règle ajoutée §3.3) — supprime les examens du circuit d'un dossier retiré
     * (via dispatch → réception → dossier). À appeler <strong>après</strong> ses enfants
     * (détails, PV, lettres de renvoi) et <strong>avant</strong> les dispatchs (ordre FK-safe).
     */
    @Modifying
    @Query("delete from Examen e where e.idDispatch in "
            + "(select di.idDispatch from Dispatch di where di.idReception in "
            + "(select r.idReception from Reception r where r.idDossier = :idDossier))")
    int deleteParDossier(@Param("idDossier") Integer idDossier);

    @Query("select e from Examen e where e.dispatch.reception.ctrlRecept.idLocalite = :loc")
    List<Examen> findVisiblesParLocalite(@Param("loc") String loc);

    /** Ce contrôleur est-il membre attributaire d'un examen ? (garde de suppression) */
    boolean existsByImCtrlMembre(String imCtrlMembre);

    @Query("select (count(e) > 0) from Examen e where e.idExamen = :id and e.dispatch.reception.ctrlRecept.idLocalite = :loc")
    boolean existsDansLocalite(@Param("id") Integer id, @Param("loc") String loc);

    /** Statut du dossier d'un examen (via examen→dispatch→réception→dossier) — pour le verrou d'édition. */
    @Query("""
            select d.statut from Examen e, Dossier d
            where e.idExamen = :idExamen and d.idDossier = e.dispatch.reception.idDossier
            """)
    Optional<String> findStatutDossierByExamen(@Param("idExamen") Integer idExamen);

    /** idDossier rattaché à un examen (examen→dispatch→réception→dossier). */
    @Query("select e.dispatch.reception.idDossier from Examen e where e.idExamen = :idExamen")
    Optional<Integer> findIdDossierByExamen(@Param("idExamen") Integer idExamen);

    /** Localité de circuit d'un examen (via la réception : examen→dispatch→réception→contrôleur récepteur). */
    @Query("select e.dispatch.reception.ctrlRecept.idLocalite from Examen e where e.idExamen = :idExamen")
    Optional<String> findLocaliteByExamen(@Param("idExamen") Integer idExamen);

    /** refeDossier du dossier rattaché à un examen (pour dériver la référence de la lettre). */
    @Query("""
            select d.refeDossier from Examen e, Dossier d
            where e.idExamen = :idExamen and d.idDossier = e.dispatch.reception.idDossier
            """)
    Optional<String> findRefeDossierByExamen(@Param("idExamen") Integer idExamen);
}
