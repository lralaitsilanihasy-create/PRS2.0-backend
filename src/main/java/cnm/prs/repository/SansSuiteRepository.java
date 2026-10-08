package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.SansSuite;

public interface SansSuiteRepository extends JpaRepository<SansSuite, Long> {

    List<SansSuite> findByIdDmcOrderByIdAsc(Long idDmc);

    /** Les demandes dont l'avis n'est pas encore constaté (planificateur : avis rendu, échéance). */
    List<SansSuite> findByAvisIsNullAndIdDossierIsNotNull();
}
