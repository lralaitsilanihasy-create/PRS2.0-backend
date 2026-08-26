package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.PrmpEntiteDemande;

@Repository
public interface PrmpEntiteDemandeRepository extends JpaRepository<PrmpEntiteDemande, Integer> {

    /** Toutes les déclarations d'une inscription. */
    List<PrmpEntiteDemande> findByLogin(String login);

    /** Déclarations d'une inscription dans un état donné (ex. EN_ATTENTE). */
    List<PrmpEntiteDemande> findByLoginAndStatutDemande(String login, String statutDemande);

    /** Prochaine PK allouee par la sequence serveur {@code seq_prmp_entite_demande} (allocation atomique). */
    @Query(value = "select nextval('seq_prmp_entite_demande')", nativeQuery = true)
    Long nextIdDemande();
}
