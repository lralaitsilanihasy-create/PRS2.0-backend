package cnm.prs.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.Anomalie;

@Repository
public interface AnomalieRepository extends JpaRepository<Anomalie, Integer> {

    /**
     * Anomalies portant sur l'une des lignes de marché données — support du scoping de
     * {@code GET /api/anomalies} sur le périmètre des marchés visibles (§1, §3.1). Une anomalie
     * n'a pas de périmètre propre : elle hérite de celui de la ligne qu'elle signale.
     */
    List<Anomalie> findByIdDetailIn(Collection<Integer> idDetails);
}
