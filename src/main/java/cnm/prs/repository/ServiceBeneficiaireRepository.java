package cnm.prs.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.ServiceBeneficiaire;

@Repository
public interface ServiceBeneficiaireRepository extends JpaRepository<ServiceBeneficiaire, Integer> {

    /** Supprime les bénéficiaires d'un marché (cascade applicative à la suppression du marché). */
    long deleteByIdDetail(Integer idDetail);

    /** Plus grand ID_BENEF (0 si table vide) — pour allouer la PK à la création (PK manuelle). */
    @Query("select coalesce(max(s.idBenef), 0) from ServiceBeneficiaire s")
    int findMaxIdBenef();
}
