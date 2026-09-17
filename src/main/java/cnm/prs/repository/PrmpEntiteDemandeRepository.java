package cnm.prs.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.PrmpEntiteDemande;

@Repository
public interface PrmpEntiteDemandeRepository extends JpaRepository<PrmpEntiteDemande, Integer> {

    /** Toutes les déclarations d'une inscription. */
    List<PrmpEntiteDemande> findByLogin(String login);

    /** Déclarations d'une inscription dans un état donné (ex. EN_ATTENTE). */
    List<PrmpEntiteDemande> findByLoginAndStatutDemande(String login, String statutDemande);

    /**
     * ⚠️ Lot 6 (2026-09-17, §B1) — file des rattachements PRMP⇄entité non décidés, pour l'accueil de
     * l'Administrateur.
     */
    long countByStatutDemande(String statutDemande);

    /**
     * ⚠️ Lot 6 (2026-09-17, §B1) — jour de la <strong>plus ancienne</strong> déclaration encore dans
     * l'état donné, ou {@code null} si la file est vide. C'est l'ancienneté, et non le volume, qui dit
     * s'il y a urgence.
     */
    @Query("select min(d.dateDeclaration) from PrmpEntiteDemande d where d.statutDemande = :statutDemande")
    LocalDate premiereDeclaration(@Param("statutDemande") String statutDemande);

    /** Prochaine PK allouee par la sequence serveur {@code seq_prmp_entite_demande} (allocation atomique). */
    @Query(value = "select nextval('seq_prmp_entite_demande')", nativeQuery = true)
    Long nextIdDemande();
}
