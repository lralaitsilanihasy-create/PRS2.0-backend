package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.Dispatch;

@Repository
public interface DispatchRepository extends JpaRepository<Dispatch, Integer> {

    /**
     * Purge (⚠️ règle ajoutée §3.3) — supprime les dispatchs du circuit d'un dossier retiré
     * (via ses réceptions). À appeler <strong>après</strong> les examens/copies rattachés et
     * <strong>avant</strong> les réceptions (ordre FK-safe).
     */
    @Modifying
    @Query("delete from Dispatch di where di.idReception in "
            + "(select r.idReception from Reception r where r.idDossier = :idDossier)")
    int deleteParDossier(@Param("idDossier") Integer idDossier);

    /** Matricule du Membre attributaire d'un dispatch — pour réserver l'examen à l'attributaire (§2.4). */
    @Query("select d.imCtrlMembre from Dispatch d where d.idDispatch = :id")
    Optional<String> findImCtrlMembreById(@Param("id") Integer id);

    /** Matricule du Membre attributaire d'un dossier (via sa réception), pour le re-notifier à la complétion après renvoi. */
    @Query("select d.imCtrlMembre from Dispatch d where d.reception.idDossier = :idDossier")
    Optional<String> findImCtrlMembreByDossier(@Param("idDossier") Integer idDossier);

    /**
     * ⚠️ Frise du tableau de bord (2026-09-13) — même information <strong>EN LOT</strong> : (idDossier,
     * imCtrlMembre) de l'attributaire courant de chaque dossier d'une liste, une seule requête quelle
     * que soit sa taille. Croissant par dispatch : à plusieurs lignes pour un même dossier, la plus
     * récente est lue en dernier.
     */
    @Query("select d.reception.idDossier, d.imCtrlMembre from Dispatch d "
            + "where d.reception.idDossier in :ids order by d.idDispatch asc")
    List<Object[]> findAttributairesParDossiers(@Param("ids") java.util.Collection<Integer> ids);

    /**
     * ⚠️ <strong>Circuit du dossier</strong> (2026-09-04) — localité, dispatcheur COURANT et
     * attributaire, en <strong>une</strong> requête. Pendant, côté dossier, de
     * {@code PvExamenRepository#findCircuitByPv} : le chronométrage part du dossier, la navette part
     * du PV, mais les deux ont besoin des mêmes trois valeurs pour trancher le « deux niveaux ».
     *
     * @return {@code [localite, imCtrlDispatch, imCtrlMembre]}, ou vide si le dossier n'a pas de dispatch
     */
    @Query("select d.reception.ctrlRecept.idLocalite, d.imCtrlDispatch, d.imCtrlMembre "
            + "from Dispatch d where d.reception.idDossier = :idDossier")
    java.util.List<Object[]> findCircuitByDossier(@Param("idDossier") Integer idDossier);

    /**
     * ⚠️ 2026-09-15 — <strong>réceptions et circuits EN LOT</strong> (accueil « À faire », demande front du
     * 2026-09-14, §6) : la variante {@code in :ids} de {@link #findCircuitByDossier}, étendue aux réceptions sans
     * dispatch (un dossier prêt à dispatcher en a une) et aux faits du dispatch. Une ligne par réception, et par
     * dispatch de cette réception le cas échéant — croissant, si bien que la dernière lue est la plus récente.
     *
     * <p>Colonnes : (0) idDossier, (1) idReception, (2) localité du contrôleur de réception — la même que
     * {@code Circuit.localite} —, (3) idDispatch, (4) imCtrlDispatch, (5) imCtrlMembre, (6) instructions,
     * (7) plus grand idExamen du dispatch ({@code null} : examen non entamé).</p>
     */
    @Query("""
            select r.idDossier, r.idReception, c.idLocalite, di.idDispatch, di.imCtrlDispatch, di.imCtrlMembre,
                   di.instructions, (select max(e.idExamen) from Examen e where e.idDispatch = di.idDispatch)
            from Reception r left join r.ctrlRecept c left join Dispatch di on di.idReception = r.idReception
            where r.idDossier in :ids
            order by r.idReception asc, di.idDispatch asc
            """)
    List<Object[]> findReceptionsEtCircuitsParDossiers(@Param("ids") java.util.Collection<Integer> ids);

    /**
     * Dispatchs visibles à l'écran « Dispatch des dossiers » : on <strong>exclut</strong> les dossiers
     * redevenus <strong>BROUILLON</strong> (ex. après acceptation d'une demande de retrait, qui laisse un
     * dispatch orphelin) ou <strong>RETIRE</strong> — ils ne doivent jamais y apparaître. Les états
     * d'avancement normaux (PRET_DISPATCH → DISPATCHE → EXAMINE → … → CLOTURE) restent visibles.
     */
    @Query("select d from Dispatch d, Dossier dos "
            + "where dos.idDossier = d.reception.idDossier and dos.statut not in ('BROUILLON', 'RETIRE')")
    List<Dispatch> findVisibles();

    @Query("select d from Dispatch d, Dossier dos "
            + "where dos.idDossier = d.reception.idDossier and d.reception.ctrlRecept.idLocalite = :loc "
            + "and dos.statut not in ('BROUILLON', 'RETIRE')")
    List<Dispatch> findVisiblesParLocalite(@Param("loc") String loc);

    @Query("select (count(d) > 0) from Dispatch d where d.idDispatch = :id and d.reception.ctrlRecept.idLocalite = :loc")
    boolean existsDansLocalite(@Param("id") Integer id, @Param("loc") String loc);

    /** Localité d'un dispatch (via réception → contrôleur réceptionnaire). */
    @Query("select d.reception.ctrlRecept.idLocalite from Dispatch d where d.idDispatch = :id")
    String findLocaliteById(@Param("id") Integer id);

    /** Vrai si un dispatch existe déjà pour cette réception (anti-doublon, §3.2). */
    boolean existsByIdReception(Integer idReception);

    /**
     * ⚠️ Intérim désigné (2026-09-21, §B3) — dossiers dont {@code im} est l'attributaire courant et dont le
     * traitement est en cours (statuts fournis) : le cumul « déjà attributaire de dossiers » est signalé à la
     * désignation d'un intérimaire, jamais interdit.
     */
    @Query("select count(d) from Dispatch d, Dossier dos where dos.idDossier = d.reception.idDossier "
            + "and d.imCtrlMembre = :im and dos.statut in :statuts")
    long countAttributionsEnCours(@Param("im") String im, @Param("statuts") java.util.Collection<String> statuts);

    /** Ce contrôleur figure-t-il sur un dispatch (dispatcheur / CC / membre) ? (garde de suppression) */
    @Query("select (count(d) > 0) from Dispatch d "
            + "where d.imCtrlDispatch = :im or d.imCtrlCc = :im or d.imCtrlMembre = :im")
    boolean existsAvecControleur(@Param("im") String im);

    // La reprise « association CC invalide » (règle modifiée 2026-08-15) vit désormais dans la
    // migration Flyway V4 (LOT 2, 2026-08-26) — l'ex-requête effacerAssociationCcInvalide
    // n'a plus d'appelant.


    /** Prochaine PK allouee par la sequence serveur {@code seq_dispatch} (allocation atomique). */
    @Query(value = "select nextval('seq_dispatch')", nativeQuery = true)
    Long nextIdDispatch();
}
