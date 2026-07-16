package cnm.prs.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.CopieDossier;

@Repository
public interface CopieDossierRepository extends JpaRepository<CopieDossier, Integer> {

    /**
     * Purge (⚠️ règle ajoutée §3.3) — supprime les copies de dossier d'un dossier retiré (FK directe
     * {@code ID_DOSSIER}). À appeler <strong>avant</strong> les dispatchs (FK {@code ID_DISPATCH}).
     */
    @Modifying
    @Query("delete from CopieDossier c where c.idDossier = :idDossier")
    int deleteParDossier(@Param("idDossier") Integer idDossier);
}
