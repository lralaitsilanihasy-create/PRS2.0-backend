package cnm.prs.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import cnm.prs.entity.TacheDossier;

/** Occurrences de taches chronometrees (append-only) — chronometrage des delais, 2026-09-01. */
public interface TacheDossierRepository extends JpaRepository<TacheDossier, Integer> {

    List<TacheDossier> findByIdDossierOrderByDatePriseEnChargeAsc(Integer idDossier);

    /** Chargement EN LOT pour l'enrichissement des listes de dossiers (une requete, quelle que soit la taille). */
    @Query("select t from TacheDossier t where t.idDossier in :ids order by t.datePriseEnCharge asc")
    List<TacheDossier> findParDossiers(@Param("ids") Collection<Integer> ids);

    /** Tache encore ouverte d'un dossier pour une etape donnee (au plus une, par construction). */
    @Query("select t from TacheDossier t where t.idDossier = :idDossier and t.etape = :etape "
            + "and t.dateFin is null order by t.occurrence desc")
    List<TacheDossier> ouvertes(@Param("idDossier") Integer idDossier, @Param("etape") String etape);

    /** Rang de la prochaine occurrence pour ce dossier et cette etape. */
    @Query("select coalesce(max(t.occurrence), 0) from TacheDossier t "
            + "where t.idDossier = :idDossier and t.etape = :etape")
    Integer dernierRang(@Param("idDossier") Integer idDossier, @Param("etape") String etape);

    /** Derniere occurrence CLOSE d'une etape — sert aux bornes du compteur global. */
    @Query("select t from TacheDossier t where t.idDossier = :idDossier and t.etape = :etape "
            + "and t.dateFin is not null order by t.dateFin desc")
    List<TacheDossier> closes(@Param("idDossier") Integer idDossier, @Param("etape") String etape);

    @Query(value = "select nextval('seq_tache_dossier')", nativeQuery = true)
    Integer nextId();

    Optional<TacheDossier> findFirstByIdDossierAndEtapeAndDateFinIsNull(Integer idDossier, String etape);
}
