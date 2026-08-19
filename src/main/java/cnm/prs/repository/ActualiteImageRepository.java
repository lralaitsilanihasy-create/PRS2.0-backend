package cnm.prs.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.ActualiteImage;

/**
 * Accès aux images des actualités ({@code t_actualite_image}).
 */
public interface ActualiteImageRepository extends JpaRepository<ActualiteImage, Integer> {

    Optional<ActualiteImage> findByIdImageAndIdActualite(Integer idImage, Integer idActualite);

    /** Projection fermée : métadonnées d'affichage seulement, sans charger le contenu binaire. */
    interface Meta {
        Integer getIdImage();

        Integer getIdActualite();

        String getNomFichier();

        Long getTailleOctets();

        Integer getOrdre();
    }

    List<Meta> findMetaByIdActualiteInOrderByOrdreAsc(Collection<Integer> ids);

    long countByIdActualite(Integer idActualite);
}
