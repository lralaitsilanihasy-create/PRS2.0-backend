package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.Lot;

@Repository
public interface LotRepository extends JpaRepository<Lot, Integer> {

    /** Lots d'un marché (pour cascader leurs tranches avant suppression). */
    List<Lot> findByIdDetail(Integer idDetail);

    /** Supprime les lots d'un marché (cascade applicative — leurs tranches doivent être retirées avant). */
    long deleteByIdDetail(Integer idDetail);
}
