package cnm.prs.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
