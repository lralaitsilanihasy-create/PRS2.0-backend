package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.IndicateurCtrl;

@Repository
public interface IndicateurCtrlRepository extends JpaRepository<IndicateurCtrl, Integer> {

    /**
     * Indicateurs d'un contrôleur donné — un contrôleur ne consulte que <strong>sa propre</strong>
     * performance ; la vue « tous les membres de toutes les commissions » reste au Président (§3.8, Module 09).
     */
    List<IndicateurCtrl> findByImControleur(String imControleur);

    /** Supprime les indicateurs d'un contrôleur (nettoyage à la suppression du contrôleur). */
    long deleteByImControleur(String imControleur);
}
