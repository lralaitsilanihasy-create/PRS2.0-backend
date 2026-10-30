package cnm.prs.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.PublicationAvis;

/** ⚠️ V154 (2026-10-30, « pieces-ddm-donnees », §B2) — les publications de l'avis déposées sur les procédures. */
public interface PublicationAvisRepository extends JpaRepository<PublicationAvis, Long> {

    /** La publication en vigueur de la procédure. */
    Optional<PublicationAvis> findFirstByIdDmcAndRemplaceeLeIsNull(Long idDmc);
}
