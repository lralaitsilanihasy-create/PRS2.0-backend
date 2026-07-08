package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.Prmp;

@Repository
public interface PrmpRepository extends JpaRepository<Prmp, String> {

    /**
     * PRMP rattachées à une localité <strong>via leurs entités contractantes actives</strong> :
     * {@code t_prmp_entite} (actif) → {@code tr_entite_contract.ID_LOCALITE}. Distinct, éventuellement vide.
     */
    @Query("select distinct p from Prmp p, PrmpEntite pe, EntiteContract e "
            + "where pe.idPrmp = p.idPrmp and pe.actif = true "
            + "and e.idEntiteContract = pe.idEntiteContract and e.idLocalite = :loc")
    List<Prmp> findByLocaliteViaEntitesActives(@Param("loc") String loc);
}
