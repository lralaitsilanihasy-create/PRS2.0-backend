package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.ObservationPv;

@Repository
public interface ObservationPvRepository extends JpaRepository<ObservationPv, Integer> {

    /** Périmètre figé d'un dossier, dans l'ordre du PV. */
    List<ObservationPv> findByIdDossierOrderByOrdreAscIdObservationPvAsc(Integer idDossier);

    /** Le périmètre a-t-il déjà été généré pour ce PV ? (génération idempotente) */
    boolean existsByIdPv(Integer idPv);

    /** Le circuit des observations est-il actif pour ce dossier ? (garde anti-saisie libre) */
    boolean existsByIdDossier(Integer idDossier);

    /** Purge (retrait de dossier) — après l'historique {@code t_suivi_observation}. */
    @Modifying
    @Query("delete from ObservationPv o where o.idDossier = :idDossier")
    int deleteParDossier(@Param("idDossier") Integer idDossier);
}
