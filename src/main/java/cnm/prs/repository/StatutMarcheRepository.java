package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.StatutMarche;

@Repository
public interface StatutMarcheRepository extends JpaRepository<StatutMarche, String> {

    /** Ordre d'affichage du référentiel : le rang saisi d'abord, le code pour départager. */
    List<StatutMarche> findAllByOrderByOrdreAscCodeAsc();

    /** Codes actifs, pour le message de refus d'un code inconnu. */
    List<StatutMarche> findByActifTrueOrderByOrdreAscCodeAsc();
}
