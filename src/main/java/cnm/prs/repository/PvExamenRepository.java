package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.PvExamen;

@Repository
public interface PvExamenRepository extends JpaRepository<PvExamen, Integer> {

    /** Nombre de PV à un statut donné (ex. {@code SIGNE} = définitifs). */
    long countByStatutPv(String statutPv);

    /**
     * Purge (⚠️ règle ajoutée §3.3) — supprime les PV (projets, non signés) des examens du circuit d'un
     * dossier retiré (via examen → dispatch → réception → dossier). À appeler <strong>après</strong> ses
     * enfants (navettes, vérifications) et <strong>avant</strong> les examens. Un dossier retirable est
     * « avant PV signé » : les PV concernés sont donc des projets.
     */
    @Modifying
    @Query("delete from PvExamen pv where pv.idExamen in "
            + "(select e.idExamen from Examen e where e.idDispatch in "
            + "(select di.idDispatch from Dispatch di where di.idReception in "
            + "(select r.idReception from Reception r where r.idDossier = :idDossier)))")
    int deleteParDossier(@Param("idDossier") Integer idDossier);

    /** Nombre de PV dont le statut diffère de la valeur donnée (ex. {@code <> SIGNE} = projets). */
    long countByStatutPvNot(String statutPv);

    /** Nombre de projets de PV (≠ SIGNE) d'une localité (via réception → contrôleur) — compteur CC. */
    @Query("select count(pv) from PvExamen pv where pv.statutPv <> 'SIGNE' "
            + "and pv.examen.dispatch.reception.ctrlRecept.idLocalite = :loc")
    long countProjetsParLocalite(@Param("loc") String loc);

    /** Nombre de PV signés (définitifs) d'une localité (via réception → contrôleur) — compteur CC. */
    @Query("select count(pv) from PvExamen pv where pv.statutPv = 'SIGNE' "
            + "and pv.examen.dispatch.reception.ctrlRecept.idLocalite = :loc")
    long countDefinitifsParLocalite(@Param("loc") String loc);

    /** Plus grand ID_PV existant (0 si table vide) — pour allouer la PK assignée à la soumission d'examen. */
    @Query("select coalesce(max(p.idPv), 0) from PvExamen p")
    Integer findMaxId();

    /**
     * Identifiant(s) PRMP rattaché(s) à un PV, via la chaîne
     * PV → examen → dispatch → réception → dossier → PPM. Sert à notifier la PRMP du PV signé.
     */
    @Query("""
            select distinct p.idPrmp from PvExamen pv, Examen e, Dispatch d, Reception r, Ppm p
            where pv.idPv = :idPv and e.idExamen = pv.idExamen and d.idDispatch = e.idDispatch
              and r.idReception = d.idReception and p.idDossier = r.idDossier and p.idPrmp is not null
            """)
    List<String> findIdPrmpByPv(@Param("idPv") Integer idPv);

    /** Statut d'un PV (précondition de la vérification : doit être SIGNE, §3.6). */
    @Query("select pv.statutPv from PvExamen pv where pv.idPv = :id")
    Optional<String> findStatutById(@Param("id") Integer id);

    /** Localité du dossier d'un PV (via examen→dispatch→réception→contrôleur réceptionnaire). */
    @Query("select pv.examen.dispatch.reception.ctrlRecept.idLocalite from PvExamen pv where pv.idPv = :idPv")
    Optional<String> findLocaliteByPv(@Param("idPv") Integer idPv);

    /** Identifiant du dossier d'un PV (via examen→dispatch→réception). */
    @Query("select pv.examen.dispatch.reception.idDossier from PvExamen pv where pv.idPv = :idPv")
    Optional<Integer> findIdDossierByPv(@Param("idPv") Integer idPv);

    /** Nombre de PV <strong>SIGNÉS</strong> rattachés à un dossier (via examen→dispatch→réception) — garde-fou de cohérence dossier↔PV. */
    @Query("select count(pv) from PvExamen pv where pv.statutPv = 'SIGNE' and pv.examen.dispatch.reception.idDossier = :idDossier")
    long countSignesParDossier(@Param("idDossier") Integer idDossier);

    /** Code d'avis (tr_avis) d'un PV — sert au branchement du circuit à la signature (⚠️ règle ajoutée). */
    @Query("select pv.idAvis from PvExamen pv where pv.idPv = :idPv")
    Optional<String> findIdAvisByPv(@Param("idPv") Integer idPv);

    /** Membre attributaire d'un examen (via dispatch) — source de vérité de l'imCtrlMembre du PV (⚠️ règle ajoutée). */
    @Query("select e.dispatch.imCtrlMembre from Examen e where e.idExamen = :idExamen")
    Optional<String> findImCtrlMembreByExamen(@Param("idExamen") Integer idExamen);

    @Query("select pv from PvExamen pv where pv.examen.dispatch.reception.ctrlRecept.idLocalite = :loc")
    List<PvExamen> findVisiblesParLocalite(@Param("loc") String loc);

    @Query("select (count(pv) > 0) from PvExamen pv where pv.idPv = :id "
            + "and pv.examen.dispatch.reception.ctrlRecept.idLocalite = :loc")
    boolean existsDansLocalite(@Param("id") Integer id, @Param("loc") String loc);

    /** Projets de PV (non signés) — tous (Président/Admin). */
    @Query("select pv from PvExamen pv where pv.statutPv <> 'SIGNE'")
    List<PvExamen> findProjets();

    /** Projets de PV (non signés) d'une localité. */
    @Query("select pv from PvExamen pv where pv.statutPv <> 'SIGNE' "
            + "and pv.examen.dispatch.reception.ctrlRecept.idLocalite = :loc")
    List<PvExamen> findProjetsParLocalite(@Param("loc") String loc);

    /** PV définitifs (signés) — tous. */
    @Query("select pv from PvExamen pv where pv.statutPv = 'SIGNE'")
    List<PvExamen> findDefinitifs();

    /** PV définitifs (signés) d'une localité. */
    @Query("select pv from PvExamen pv where pv.statutPv = 'SIGNE' "
            + "and pv.examen.dispatch.reception.ctrlRecept.idLocalite = :loc")
    List<PvExamen> findDefinitifsParLocalite(@Param("loc") String loc);

    /** refeDossier du dossier rattaché à un examen (examen → dispatch → réception → dossier). */
    @Query("select e.dispatch.reception.dossier.refeDossier from Examen e where e.idExamen = :idExamen")
    Optional<String> findRefeDossierByExamen(@Param("idExamen") Integer idExamen);

    /** Vrai si un PV porte déjà cette référence (unicité applicative). */
    boolean existsByRefePv(String refePv);

    /** Ce contrôleur figure-t-il sur un PV (président / CC / membre) ? (garde de suppression) */
    @Query("select (count(pv) > 0) from PvExamen pv "
            + "where pv.imCtrlPresident = :im or pv.imCtrlCc = :im or pv.imCtrlMembre = :im")
    boolean existsAvecControleur(@Param("im") String im);
}
