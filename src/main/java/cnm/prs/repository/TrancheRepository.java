package cnm.prs.repository;

import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.Tranche;

@Repository
public interface TrancheRepository extends JpaRepository<Tranche, Integer> {

    /** Supprime les tranches des lots donnés (cascade applicative à la suppression du marché). */
    long deleteByIdLotIn(Collection<Integer> idLots);
}
