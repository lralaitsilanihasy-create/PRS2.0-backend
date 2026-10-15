package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.PublicationAgpm;

public interface PublicationAgpmRepository extends JpaRepository<PublicationAgpm, Long> {

    /** La publication en vigueur déposée sur ce plan. */
    Optional<PublicationAgpm> findFirstByIdDossierAndRemplaceeLeIsNull(Integer idDossier);

    /** Toutes les publications déposées sur ces plans, de la plus récente à la plus ancienne. */
    List<PublicationAgpm> findByIdDossierInOrderByDeposeLeDescIdPublicationDesc(List<Integer> idDossiers);
}
