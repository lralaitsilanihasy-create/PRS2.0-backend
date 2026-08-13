package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.PieceJointeDossier;

@Repository
public interface PieceJointeDossierRepository extends JpaRepository<PieceJointeDossier, Integer> {

    /** Pièces d'un dossier. */
    List<PieceJointeDossier> findByIdDossier(Integer idDossier);

    /** Vrai si une pièce du type donné est déjà attachée au dossier (contrôle des obligatoires). */
    boolean existsByIdDossierAndIdTypePiece(Integer idDossier, Integer idTypePiece);

    /**
     * Vrai si ≥1 pièce complémentaire a été déposée pour CETTE lettre de renvoi (⚠️ règle ajoutée
     * 2026-08-02 — garde de {@code …/transmettre-complements} : pas de réexamen sans les pièces).
     */
    boolean existsByIdDossierAndIdLettreAndApresLettreRenvoiTrue(Integer idDossier, Integer idLettre);
}
