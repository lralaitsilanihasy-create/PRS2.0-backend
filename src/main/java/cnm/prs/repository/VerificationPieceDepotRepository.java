package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.VerificationPieceDepot;

public interface VerificationPieceDepotRepository extends JpaRepository<VerificationPieceDepot, Integer> {

    /** Historique complet des vérifications d'un dossier (ASC : l'état courant = dernière ligne par type). */
    List<VerificationPieceDepot> findByIdDossierOrderByDateVerifAscIdVerifPieceAsc(Integer idDossier);
}
