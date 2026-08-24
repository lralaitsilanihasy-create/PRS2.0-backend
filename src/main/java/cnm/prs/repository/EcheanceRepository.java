package cnm.prs.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.Echeance;

@Repository
public interface EcheanceRepository extends JpaRepository<Echeance, Integer> {

    /**
     * Jalons portant sur l'une des lignes de marché données — support du scoping de
     * {@code GET /api/echeances} sur le périmètre des marchés visibles (§1, §3.1). Un jalon n'a pas
     * de périmètre propre : il hérite de celui de sa ligne de marché ({@code t_echeance.ID_DETAIL}).
     */
    List<Echeance> findByIdDetailIn(Collection<Integer> idDetails);

    /**
     * Jalons à alerter (§3.1, Module 04) : non encore alertés, non réalisés
     * ({@code DATE_REELLE} nulle) et dont la date prévue tombe dans la fenêtre [debut, fin].
     */
    @Query("""
            select e from Echeance e
            where (e.alerteEnvoyee is null or e.alerteEnvoyee = false)
              and e.dateReelle is null
              and e.datePrevue between :debut and :fin
            """)
    List<Echeance> findJalonsAAlerter(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);
}
