package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.SansSuite;

public interface SansSuiteRepository extends JpaRepository<SansSuite, Long> {

    List<SansSuite> findByIdDmcOrderByIdAsc(Long idDmc);

    /** Les demandes dont l'avis n'est pas encore constaté (planificateur : avis rendu, échéance). */
    List<SansSuite> findByAvisIsNullAndIdDossierIsNotNull();

    /** ⚠️ R2 (manuel révisé, V102) — le dossier DSS est né d'une demande faite depuis la procédure : « en ligne ». */
    boolean existsByIdDossier(Integer idDossier);

    /** ⚠️ R4 (manuel révisé, V104) — la demande dont ce dossier DSS est né (sa procédure). */
    java.util.Optional<SansSuite> findFirstByIdDossier(Integer idDossier);
}
