package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.IndicateurPrmp;

@Repository
public interface IndicateurPrmpRepository extends JpaRepository<IndicateurPrmp, Integer> {

    /** Indicateurs d'une PRMP — la PRMP (et l'UGPM de sa tutelle) ne consulte que les siens (§1). */
    List<IndicateurPrmp> findByIdPrmp(String idPrmp);

    /** Existe-t-il au moins un indicateur pour cette PRMP ? (garde de suppression PRMP) */
    boolean existsByIdPrmp(String idPrmp);
}
