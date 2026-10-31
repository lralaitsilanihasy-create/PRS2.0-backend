package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.BonCommande;

/** ⚠️ V126 (lot MS-3) — les bons de commande d'un contrat en vigueur. */
public interface BonCommandeRepository extends JpaRepository<BonCommande, Long> {

    List<BonCommande> findByIdContratOrderByNumeroAsc(Long idContrat);

    /** ⚠️ 2026-10-30 (« performance », reliquat) — les bons de plusieurs contrats, en une requête (liste des contrats). */
    List<BonCommande> findByIdContratInOrderByIdContratAscNumeroAsc(java.util.Collection<Long> idContrats);

    Optional<BonCommande> findFirstByIdContratOrderByNumeroDesc(Long idContrat);

    List<BonCommande> findByIdRemiseOrderByNumeroAsc(Long idRemise);

    List<BonCommande> findByIdOffreOrderByEmisLeDescIdDesc(String idOffre);
}
