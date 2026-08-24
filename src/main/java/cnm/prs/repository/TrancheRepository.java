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

    /** Plus grand ID_TRANCHE existant (0 si vide) — PK allouée serveur (Voie B). */
    @Query("select coalesce(max(t.idTranche), 0) from Tranche t")
    Integer findMaxIdTranche();

    /** Supprime les tranches des lots donnés (cascade applicative à la suppression du marché). */
    long deleteByIdLotIn(Collection<Integer> idLots);
}
