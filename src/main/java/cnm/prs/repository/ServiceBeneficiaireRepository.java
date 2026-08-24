package cnm.prs.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.ServiceBeneficiaire;

@Repository
public interface ServiceBeneficiaireRepository extends JpaRepository<ServiceBeneficiaire, Integer> {

    /** Bénéficiaires d'un marché — copie d'une version à l'autre et comparaison de diff. */
    List<ServiceBeneficiaire> findByIdDetail(Integer idDetail);

    /**
     * Bénéficiaires des lignes de marché données — support du scoping de
     * {@code GET /api/service-beneficiaires} sur le périmètre des marchés visibles (§1, §3.1).
     */
    List<ServiceBeneficiaire> findByIdDetailIn(Collection<Integer> idDetails);

    /** Supprime les bénéficiaires d'un marché (cascade applicative à la suppression du marché). */
    long deleteByIdDetail(Integer idDetail);

    /** Plus grand ID_BENEF (0 si table vide) — pour allouer la PK à la création (PK manuelle). */
    @Query("select coalesce(max(s.idBenef), 0) from ServiceBeneficiaire s")
    int findMaxIdBenef();
}
