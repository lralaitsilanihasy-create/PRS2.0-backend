package cnm.prs.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.LettreRenvoiLue;

@Repository
public interface LettreRenvoiLueRepository extends JpaRepository<LettreRenvoiLue, Integer> {

    /** Vrai si la PRMP a déjà lu la lettre (anti-doublon + flag {@code lue} du DTO). */
    boolean existsByIdLettreAndIdPrmp(Integer idLettre, String idPrmp);

    /**
     * Purge (⚠️ règle ajoutée §3.3) — supprime les accusés de lecture des lettres de renvoi d'un dossier
     * retiré (via {@code LettreRenvoi.idDossier}). À appeler <strong>avant</strong> les lettres de renvoi.
     */
    @Modifying
    @Query("delete from LettreRenvoiLue lu where lu.idLettre in "
            + "(select l.idLettre from LettreRenvoi l where l.idDossier = :idDossier)")
    int deleteParDossier(@Param("idDossier") Integer idDossier);
}
