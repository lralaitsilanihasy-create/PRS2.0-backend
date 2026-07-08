package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.Verification;

@Repository
public interface VerificationRepository extends JpaRepository<Verification, Integer> {

    @Query("select v from Verification v where v.reception.ctrlRecept.idLocalite = :loc")
    List<Verification> findVisiblesParLocalite(@Param("loc") String loc);

    /** Ce contrôleur a-t-il réalisé au moins une vérification ? (garde de suppression) */
    boolean existsByImCtrlVerif(String imCtrlVerif);

    @Query("select (count(v) > 0) from Verification v where v.idVerification = :id and v.reception.ctrlRecept.idLocalite = :loc")
    boolean existsDansLocalite(@Param("id") Integer id, @Param("loc") String loc);

    /** Passages (vérifications) d'un dossier, le plus récent d'abord — pour retrouver la dernière vérification. */
    @Query("""
            select v from Verification v where v.reception.idDossier = :idDossier
            order by v.dateVerif desc, v.idVerification desc
            """)
    List<Verification> findPassagesDuDossier(@Param("idDossier") Integer idDossier);

    /** Nombre de dossiers conformes : ayant une vérification avec OBS_LEVEES = true (§3.2). */
    @Query("select count(distinct v.reception.idDossier) from Verification v where v.obsLevees = true")
    long compterDossiersConformes();

    /** Nombre de dossiers conformes d'une localité (tableau de bord du CC, §3.3). */
    @Query("""
            select count(distinct v.reception.idDossier) from Verification v
            where v.obsLevees = true
              and exists (select 1 from Dossier d
                          where d.idDossier = v.reception.idDossier and d.idLocalite = :loc)
            """)
    long compterDossiersConformesParLocalite(@Param("loc") String loc);
}
