package cnm.prs.repository;

import java.util.Collection;
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

    /**
     * ⚠️ Rattachements (2026-09-01) — matricule du Membre ayant EXAMINÉ un dossier, le plus récent
     * d'abord. C'est lui qui détermine le Vérificateur cible, et non le co-signataire du PV : les deux
     * sont des personnes distinctes depuis le 2026-08-28, et c'est l'examinateur qui porte l'instruction.
     */
    @Query("""
            select e.imCtrlMembre from Examen e
            where e.dispatch.reception.idDossier = :idDossier and e.imCtrlMembre is not null
            order by e.idExamen desc
            """)
    List<String> findImCtrlMembreParDossier(@Param("idDossier") Integer idDossier);

    /**
     * ⚠️ Rattachements (2026-09-01) — couples (idDossier, Membre examinateur) pour un LOT de dossiers.
     * Sert l'enrichissement en lot des listes : sans elle, résoudre la cible dossier par dossier
     * réintroduirait le N+1 que {@code DossierService.enrichir} avait précisément supprimé.
     */
    @Query("""
            select e.dispatch.reception.idDossier, e.imCtrlMembre from Examen e
            where e.dispatch.reception.idDossier in :idsDossiers and e.imCtrlMembre is not null
            order by e.idExamen asc
            """)
    List<Object[]> findMembresParDossiers(@Param("idsDossiers") Collection<Integer> idsDossiers);

    /** Localité de circuit d'un examen (via la réception : examen→dispatch→réception→contrôleur récepteur). */
    @Query("select e.dispatch.reception.ctrlRecept.idLocalite from Examen e where e.idExamen = :idExamen")
    Optional<String> findLocaliteByExamen(@Param("idExamen") Integer idExamen);

    /** refeDossier du dossier rattaché à un examen (pour dériver la référence de la lettre). */
    @Query("""
            select d.refeDossier from Examen e, Dossier d
            where e.idExamen = :idExamen and d.idDossier = e.dispatch.reception.idDossier
            """)
    Optional<String> findRefeDossierByExamen(@Param("idExamen") Integer idExamen);


    /** Prochaine PK allouee par la sequence serveur {@code seq_examen} (allocation atomique). */
    @Query(value = "select nextval('seq_examen')", nativeQuery = true)
    Long nextIdExamen();

    /**
     * ⚠️ Réattribution (2026-09-03) — un dispatch porte-t-il déjà un examen ? Le re-ciblage d'un
     * dispatch dont l'examen est entamé est refusé (409) : le circuit propre passe par « Retirer »,
     * qui purge l'aval, plutôt que par un changement d'attributaire qui laisserait l'examen d'un autre.
     */
    boolean existsByIdDispatch(Integer idDispatch);
}
