package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.VersionDossier;

/**
 * ⚠️ Versions archivées (2026-09-06) — en-têtes des versions archivées d'un dossier. Une version ne
 * s'écrit qu'à sa création (archivage) : aucune méthode de mise à jour, l'entité est immuable.
 */
@Repository
public interface VersionDossierRepository extends JpaRepository<VersionDossier, Integer> {

    List<VersionDossier> findByIdDossierOrderByNumeroAsc(Integer idDossier);

    Optional<VersionDossier> findByIdDossierAndNumero(Integer idDossier, Integer numero);

    Optional<VersionDossier> findFirstByIdDossierOrderByNumeroDesc(Integer idDossier);

    /** Dernière version d'une origine donnée — pour le diff de rectification, qui juge le dernier cycle. */
    Optional<VersionDossier> findFirstByIdDossierAndOrigineOrderByNumeroDesc(Integer idDossier, String origine);

    /** Vrai si le cycle courant a déjà été archivé (les PUT suivants du même cycle ne re-figent pas). */
    boolean existsByIdDossierAndOrigineAndCycle(Integer idDossier, String origine, Integer cycle);

    /** Purge avec le circuit — après les lignes et leurs enfants (FK entrantes). */
    @Modifying
    @Query("delete from VersionDossier v where v.idDossier = :idDossier")
    int deleteParDossier(@Param("idDossier") Integer idDossier);
}
