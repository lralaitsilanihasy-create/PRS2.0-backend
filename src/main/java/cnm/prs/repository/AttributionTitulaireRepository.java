package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.AttributionTitulaire;

/** ⚠️ V122 (tranche MT-b1) — les titulaires retenus d'un lot de contrat-cadre multi-attributaire. */
public interface AttributionTitulaireRepository extends JpaRepository<AttributionTitulaire, AttributionTitulaire.Cle> {

    /** Les titulaires du lot, dans l'ordre du classement. */
    List<AttributionTitulaire> findByIdDmcAndLotOrderByRangAscIdOffreAsc(Long idDmc, Integer lot);

    /** ⚠️ MT-b2 — le titulaire d'une offre (une offre n'est titulaire que d'un lot). */
    List<AttributionTitulaire> findByIdOffre(String idOffre);
}
