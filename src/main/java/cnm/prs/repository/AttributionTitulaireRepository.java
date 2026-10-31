package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.AttributionTitulaire;

/** ⚠️ V122 (tranche MT-b1) — les titulaires retenus d'un lot de contrat-cadre multi-attributaire. */
public interface AttributionTitulaireRepository extends JpaRepository<AttributionTitulaire, AttributionTitulaire.Cle> {

    /** Les titulaires du lot, dans l'ordre du classement. */
    List<AttributionTitulaire> findByIdDmcAndLotOrderByRangAscIdOffreAsc(Long idDmc, Integer lot);

    /** ⚠️ 2026-10-30 (« performance », reliquat) — les titulaires de plusieurs procédures, en une requête (liste des contrats). */
    List<AttributionTitulaire> findByIdDmcInOrderByIdDmcAscLotAscRangAscIdOffreAsc(java.util.Collection<Long> idDmcs);

    /** ⚠️ MT-b2 — le titulaire d'une offre (une offre n'est titulaire que d'un lot). */
    List<AttributionTitulaire> findByIdOffre(String idOffre);
}
