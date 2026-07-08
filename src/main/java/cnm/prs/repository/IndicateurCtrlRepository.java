package cnm.prs.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.IndicateurCtrl;

@Repository
public interface IndicateurCtrlRepository extends JpaRepository<IndicateurCtrl, Integer> {

    /** Supprime les indicateurs d'un contrôleur (nettoyage à la suppression du contrôleur). */
    long deleteByImControleur(String imControleur);
}
