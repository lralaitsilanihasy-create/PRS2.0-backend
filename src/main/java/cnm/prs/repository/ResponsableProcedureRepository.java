package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.ResponsableProcedure;

/** ⚠️ V50 (2026-09-27, remise électronique, §B5) — le titulaire actif d'une procédure, et ceux d'un acteur. */
@Repository
public interface ResponsableProcedureRepository extends JpaRepository<ResponsableProcedure, Long> {

    /** Le titulaire actif (désignation non retirée) ; un seul par DMC (index partiel unique). */
    Optional<ResponsableProcedure> findFirstByIdDmcAndDateRetraitIsNull(Long idDmc);

    /** ⚠️ 2026-10-30 (« performance », §B1) — les titulaires actifs de plusieurs DMC, en une requête (préchargement des listes). */
    List<ResponsableProcedure> findByIdDmcInAndDateRetraitIsNullOrderByIdAsc(java.util.Collection<Long> idDmcs);

    /** Les désignations actives d'un acteur (toutes ses procédures). */
    List<ResponsableProcedure> findByImResponsableAndDateRetraitIsNull(String imResponsable);
}
