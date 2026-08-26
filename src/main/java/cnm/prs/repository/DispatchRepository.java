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

    /** Ce contrôleur figure-t-il sur un dispatch (dispatcheur / CC / membre) ? (garde de suppression) */
    @Query("select (count(d) > 0) from Dispatch d "
            + "where d.imCtrlDispatch = :im or d.imCtrlCc = :im or d.imCtrlMembre = :im")
    boolean existsAvecControleur(@Param("im") String im);

    // La reprise « association CC invalide » (règle modifiée 2026-08-15) vit désormais dans la
    // migration Flyway V4 (LOT 2, 2026-08-26) — l'ex-requête effacerAssociationCcInvalide
    // n'a plus d'appelant.
}
