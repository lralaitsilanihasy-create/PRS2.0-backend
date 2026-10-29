package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.ReceptionLigne;

/** ⚠️ V148 (« bons-reception », §B3) — les quantités livrées et reçues d'une réception, par ligne du bon. */
public interface ReceptionLigneRepository extends JpaRepository<ReceptionLigne, ReceptionLigne.Cle> {

    List<ReceptionLigne> findByIdReceptionOrderByRangAsc(Long idReception);
}
