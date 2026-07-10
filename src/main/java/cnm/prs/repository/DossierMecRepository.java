package cnm.prs.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.DossierMec;

@Repository
public interface DossierMecRepository extends JpaRepository<DossierMec, Long> {

    /** Le DMC d'une ligne de marché (relation 1-1). */
    Optional<DossierMec> findByIdDetail(Integer idDetail);

    /** Une ligne de marché a-t-elle déjà un DMC ? */
    boolean existsByIdDetail(Integer idDetail);

    /** Supprime le DMC d'une ligne de marché (cascade applicative à la suppression du marché). */
    long deleteByIdDetail(Integer idDetail);
}
