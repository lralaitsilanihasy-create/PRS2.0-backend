package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.ModePassation;

@Repository
public interface ModePassationRepository extends JpaRepository<ModePassation, Integer> {

    /** Modes « appel d'offres ouvert » (drapeau AGPM) pas encore classés — reprise de {@code CATEGORIE}. */
    List<ModePassation> findByDeclencheAgpmTrueAndCategorieIsNull();
}
