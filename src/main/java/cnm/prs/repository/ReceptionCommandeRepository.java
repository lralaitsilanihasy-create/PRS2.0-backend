package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.ReceptionCommande;

/** ⚠️ V127 (lot MS-4) — les réceptions d'un bon de commande. */
public interface ReceptionCommandeRepository extends JpaRepository<ReceptionCommande, Long> {

    List<ReceptionCommande> findByIdBonOrderByIdAsc(Long idBon);
}
