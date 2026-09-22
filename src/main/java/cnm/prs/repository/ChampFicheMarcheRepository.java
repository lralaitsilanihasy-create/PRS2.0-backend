package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.ChampFicheMarche;

@Repository
public interface ChampFicheMarcheRepository extends JpaRepository<ChampFicheMarche, String> {

    /** Tout le référentiel, dans l'ordre d'affichage (rubrique puis rang) ; le filtrage par type se fait en mémoire. */
    List<ChampFicheMarche> findAllByOrderByCodeRubriqueAscRangAsc();

    List<ChampFicheMarche> findByActifTrueOrderByCodeRubriqueAscRangAsc();
}
