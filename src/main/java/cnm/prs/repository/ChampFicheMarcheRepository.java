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

    /**
     * ⚠️ 2026-10-30 (demande front « performance », §B1, §B3) — le référentiel entier, lu une fois par appel de liste (mémoire de
     * lecture ouverte, {@link MemoLecture}) ; sans mémoire, la lecture seule. Ne pas modifier la liste rendue.
     */
    default List<ChampFicheMarche> referentiel() {
        return MemoLecture.lire("champsTous", this::findAllByOrderByCodeRubriqueAscRangAsc);
    }

    /** ⚠️ 2026-10-30 — les champs actifs, même règle que {@link #referentiel()}. */
    default List<ChampFicheMarche> actifs() {
        return MemoLecture.lire("champsActifs", this::findByActifTrueOrderByCodeRubriqueAscRangAsc);
    }
}