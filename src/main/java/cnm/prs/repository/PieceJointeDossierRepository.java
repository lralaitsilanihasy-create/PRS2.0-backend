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

    /**
     * Purge des pièces d'un dossier supprimé (⚠️ audit 2026-08-27, lot D §2) — la suppression d'un
     * brouillon ne nettoyait pas cette table : chaque dossier supprimé y laissait ses pièces
     * <strong>et leur contenu binaire</strong> ({@code CONTENU bytea}), sans aucun moyen de les
     * retrouver. Appelée en tête de la cascade, avant que le dossier disparaisse.
     */
    long deleteByIdDossier(Integer idDossier);
}
