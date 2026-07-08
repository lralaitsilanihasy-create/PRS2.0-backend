package cnm.prs.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.Reception;

@Repository
public interface ReceptionRepository extends JpaRepository<Reception, Integer> {

    @Query("select r from Reception r where r.ctrlRecept.idLocalite = :loc")
    List<Reception> findVisiblesParLocalite(@Param("loc") String loc);

    @Query("select (count(r) > 0) from Reception r where r.idReception = :id and r.ctrlRecept.idLocalite = :loc")
    boolean existsDansLocalite(@Param("id") Integer id, @Param("loc") String loc);

    /** Localité d'une réception (via son contrôleur réceptionnaire). */
    @Query("select r.ctrlRecept.idLocalite from Reception r where r.idReception = :id")
    String findLocaliteById(@Param("id") Integer id);

    /** Localités déjà rattachées à un dossier via ses réceptions (par ordre de passage). */
    @Query("select r.ctrlRecept.idLocalite from Reception r "
            + "where r.idDossier = :idDossier and r.ctrlRecept.idLocalite is not null order by r.numPassage")
    List<String> findLocalitesByDossier(@Param("idDossier") Integer idDossier);

    /** Réceptions d'un dossier (filtre serveur {@code ?idDossier=} — ne charge que l'utile). */
    List<Reception> findByIdDossier(Integer idDossier);

    /**
     * Date/heure de réception la plus récente d'un dossier (navettes : on retient la dernière) —
     * source de {@code datePredispatch}. {@code null} si le dossier n'a aucune réception datée.
     */
    @Query("select max(r.dateReception) from Reception r where r.idDossier = :idDossier")
    LocalDateTime findDerniereDateReceptionByDossier(@Param("idDossier") Integer idDossier);

    /** Nombre de réceptions d'une localité (via le contrôleur réceptionnaire) — compteur du Secrétaire. */
    @Query("select count(r) from Reception r where r.ctrlRecept.idLocalite = :loc")
    long countByLocalite(@Param("loc") String loc);

    /** Vrai si le dossier a déjà au moins une réception (test « déjà réceptionné » sans charger l'historique). */
    boolean existsByIdDossier(Integer idDossier);

    /** Ce contrôleur a-t-il réceptionné au moins un dossier ? (garde de suppression) */
    boolean existsByImCtrlRecept(String imCtrlRecept);

    /** Prochaine PK réception, allouée par la séquence serveur (Voie B — l'id client est ignoré). */
    @Query(value = "select nextval('seq_reception')", nativeQuery = true)
    Long nextIdReception();

    /** Supprime les réceptions d'un dossier (cascade à la suppression du dossier brouillon ; feuilles sans dispatch). */
    void deleteByIdDossier(Integer idDossier);
}
