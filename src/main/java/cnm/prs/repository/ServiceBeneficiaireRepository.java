package cnm.prs.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.ServiceBeneficiaire;

@Repository
public interface ServiceBeneficiaireRepository extends JpaRepository<ServiceBeneficiaire, Integer> {

    /** Supprime les bénéficiaires d'un marché (cascade applicative à la suppression du marché). */
    long deleteByIdDetail(Integer idDetail);
}
