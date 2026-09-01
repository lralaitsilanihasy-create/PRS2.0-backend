package cnm.prs.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import cnm.prs.entity.SuspensionDossier;

/** Fenetres d'attente PRMP (compteur net CNM) — chronometrage des delais, 2026-09-01. */
public interface SuspensionDossierRepository extends JpaRepository<SuspensionDossier, Integer> {

    List<SuspensionDossier> findByIdDossierOrderByDebutAsc(Integer idDossier);

    @Query("select s from SuspensionDossier s where s.idDossier in :ids")
    List<SuspensionDossier> findParDossiers(@Param("ids") Collection<Integer> ids);

    Optional<SuspensionDossier> findFirstByIdDossierAndFinIsNullOrderByDebutDesc(Integer idDossier);

    @Query(value = "select nextval('seq_suspension_dossier')", nativeQuery = true)
    Integer nextId();
}
