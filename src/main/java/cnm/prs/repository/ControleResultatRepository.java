package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import cnm.prs.entity.ControleResultat;

/** ⚠️ R5 (manuel révisé, lot 1, §B6 ; V105) — les résultats de contrôle d'un dossier, par soumission. */
public interface ControleResultatRepository extends JpaRepository<ControleResultat, Long> {

    List<ControleResultat> findByIdDossierAndNumeroSoumissionOrderByIdControleAsc(Integer idDossier, Integer numeroSoumission);

    List<ControleResultat> findByIdDossierOrderByNumeroSoumissionAscIdControleAsc(Integer idDossier);

    /** Le dernier numéro de soumission contrôlé pour ce dossier ; {@code null} s'il n'y en a pas. */
    @Query("select max(c.numeroSoumission) from ControleResultat c where c.idDossier = :idDossier")
    Integer maxNumero(@Param("idDossier") Integer idDossier);
}
