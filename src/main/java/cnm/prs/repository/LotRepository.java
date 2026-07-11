package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.Lot;

@Repository
public interface LotRepository extends JpaRepository<Lot, Integer> {

    /** Lots d'un marché (pour cascader leurs tranches avant suppression). */
    List<Lot> findByIdDetail(Integer idDetail);

    /** Plus grand ID_LOT existant (0 si vide) — PK allouée serveur (Voie B). */
    @Query("select coalesce(max(l.idLot), 0) from Lot l")
    Integer findMaxIdLot();

    /** Supprime les lots d'un marché (cascade applicative — leurs tranches doivent être retirées avant). */
    long deleteByIdDetail(Integer idDetail);
}
