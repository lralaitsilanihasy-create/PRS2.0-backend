package cnm.prs.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.Tranche;

@Repository
public interface TrancheRepository extends JpaRepository<Tranche, Integer> {

    /** Tranches d'un ensemble de lots — support du scoping de la liste sur le périmètre du marché parent. */
    List<Tranche> findByIdLotIn(Collection<Integer> idLots);

    /**
     * Prochaine PK de tranche, allouée par la séquence serveur (Voie B — l'id client est ignoré).
     * Remplace un {@code max(ID_TRANCHE) + 1}, que deux saisies concurrentes lisaient à l'identique.
     */
    @Query(value = "select nextval('seq_tranche')", nativeQuery = true)
    Long nextIdTranche();

    /** Supprime les tranches des lots donnés (cascade applicative à la suppression du marché). */
    long deleteByIdLotIn(Collection<Integer> idLots);
}
