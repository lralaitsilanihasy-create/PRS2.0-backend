package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.SuiviObservation;

@Repository
public interface SuiviObservationRepository extends JpaRepository<SuiviObservation, Integer> {

    /** Historique complet des observations d'un dossier (ordre chronologique de décision). */
    @Query("""
            select s from SuiviObservation s
            where s.idObservationPv in (select o.idObservationPv from ObservationPv o where o.idDossier = :idDossier)
            order by s.iteration asc, s.idSuivi asc
            """)
    List<SuiviObservation> findParDossier(@Param("idDossier") Integer idDossier);

    /** Purge (retrait de dossier) — avant le périmètre {@code t_observation_pv}. */
    @Modifying
    @Query("""
            delete from SuiviObservation s
            where s.idObservationPv in (select o.idObservationPv from ObservationPv o where o.idDossier = :idDossier)
            """)
    int deleteParDossier(@Param("idDossier") Integer idDossier);
}
