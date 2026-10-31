package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.ReceptionCommande;

/** ⚠️ V127 (lot MS-4) — les réceptions d'un bon de commande. */
public interface ReceptionCommandeRepository extends JpaRepository<ReceptionCommande, Long> {

    List<ReceptionCommande> findByIdBonOrderByIdAsc(Long idBon);

    /** ⚠️ 2026-10-31 (« filiation-dossier », §B2) — les réceptions de plusieurs bons, en une requête. */
    List<ReceptionCommande> findByIdBonInOrderByIdAsc(java.util.Collection<Long> idBons);
}
