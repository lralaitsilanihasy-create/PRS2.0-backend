package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import cnm.prs.entity.DossierInstantane;

/** ⚠️ R4 (manuel révisé, lot 1, §B5 ; V104) — les instantanés figés à la soumission d'un dossier. */
public interface DossierInstantaneRepository extends JpaRepository<DossierInstantane, Long> {

    List<DossierInstantane> findByIdDossierOrderByNumeroSoumissionAscEcranAsc(Integer idDossier);

    Optional<DossierInstantane> findFirstByIdDossierAndEcranAndNumeroSoumission(Integer idDossier, String ecran, Integer numeroSoumission);

    Optional<DossierInstantane> findFirstByIdDossierAndEcranOrderByNumeroSoumissionDesc(Integer idDossier, String ecran);

    /** Le dernier numéro de soumission figé pour ce dossier ; {@code null} s'il n'y en a pas. */
    @Query("select max(i.numeroSoumission) from DossierInstantane i where i.idDossier = :idDossier")
    Integer maxNumero(@Param("idDossier") Integer idDossier);
}
