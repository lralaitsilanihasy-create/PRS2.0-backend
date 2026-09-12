package cnm.prs.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import cnm.prs.entity.TacheDossier;

/**
 * Passages par les etapes du circuit, append-only — chronometrage des delais, 2026-09-01.
 *
 * <p>⚠️ 2026-09-12 — une ligne est une etape TERMINEE : plus de « tache ouverte », donc plus de
 * recherche d'occurrence en cours. L'ordre de reference est celui des FINS, qui est aussi la chaine dont
 * on derive les entrees ({@code ID_TACHE} departage deux fins au meme instant, dans l'ordre d'ecriture).</p>
 */
public interface TacheDossierRepository extends JpaRepository<TacheDossier, Integer> {

    @Query("select t from TacheDossier t where t.idDossier = :idDossier order by t.dateFin asc, t.idTache asc")
    List<TacheDossier> findParDossier(@Param("idDossier") Integer idDossier);

    /** Chargement EN LOT pour l'enrichissement des listes de dossiers (une requete, quelle que soit la taille). */
    @Query("select t from TacheDossier t where t.idDossier in :ids order by t.dateFin asc, t.idTache asc")
    List<TacheDossier> findParDossiers(@Param("ids") Collection<Integer> ids);

    /** Rang de la derniere occurrence pour ce dossier et cette etape (0 si aucune). */
    @Query("select coalesce(max(t.occurrence), 0) from TacheDossier t "
            + "where t.idDossier = :idDossier and t.etape = :etape")
    Integer dernierRang(@Param("idDossier") Integer idDossier, @Param("etape") String etape);

    @Query(value = "select nextval('seq_tache_dossier')", nativeQuery = true)
    Integer nextId();
}
