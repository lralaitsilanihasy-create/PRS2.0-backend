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

    /**
     * Prochaine PK de demande de rattachement, allouée par la séquence serveur (Voie B).
     *
     * <p>Remplace un {@code max(ID_DEMANDE) + 1}. À consommer <strong>une fois par ligne</strong> :
     * une inscription déclare plusieurs entités d'affilée. C'est le site le plus exposé de la série —
     * l'inscription est le seul acte du système ouvert à un utilisateur NON authentifié, donc le seul
     * dont deux exécutions simultanées ne supposent aucune coordination préalable entre acteurs.
     */
    @Query(value = "select nextval('seq_prmp_entite_demande')", nativeQuery = true)
    Long nextIdDemande();
}
