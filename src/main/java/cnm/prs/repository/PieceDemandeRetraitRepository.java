package cnm.prs.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import cnm.prs.entity.PieceDemandeRetrait;

/**
 * Accès aux lettres de demande de retrait ({@code t_piece_demande_retrait}).
 */
public interface PieceDemandeRetraitRepository extends JpaRepository<PieceDemandeRetrait, Integer> {

    Optional<PieceDemandeRetrait> findByIdDemandeRetrait(Integer idDemandeRetrait);

    void deleteByIdDemandeRetrait(Integer idDemandeRetrait);

    /** Projection fermée : métadonnées d'affichage seulement, sans charger le contenu binaire. */
    interface Meta {
        Integer getIdDemandeRetrait();

        String getNomFichier();

        Long getTailleOctets();
    }

    List<Meta> findMetaByIdDemandeRetraitIn(Collection<Integer> ids);

    /**
     * Purge des lettres de demande de retrait d'un dossier supprimé (⚠️ audit 2026-08-27, lot D §2).
     * {@code t_piece_demande_retrait} ne porte <strong>aucune FK</strong> : la suppression du dossier
     * effaçait ses demandes de retrait et laissait ici les PDF (colonne {@code CONTENU}) sans rien pour
     * les rattacher. À appeler <strong>avant</strong> {@code DemandeRetraitRepository#deleteByIdDossier}.
     */
    @Modifying
    @Query("delete from PieceDemandeRetrait p where p.idDemandeRetrait in "
            + "(select d.idDemandeRetrait from DemandeRetrait d where d.idDossier = :idDossier)")
    int deleteParDossier(@Param("idDossier") Integer idDossier);
}
