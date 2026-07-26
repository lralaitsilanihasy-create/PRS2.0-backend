package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.PrmpEntite;

@Repository
public interface PrmpEntiteRepository extends JpaRepository<PrmpEntite, Integer> {

    /** Vrai si l'entité fait partie des entités <strong>actives</strong> de la PRMP (§3.1). */
    boolean existsByIdPrmpAndIdEntiteContractAndActifTrue(String idPrmp, Integer idEntiteContract);

    /** Affectations actives d'une PRMP (ses entités contractantes). */
    List<PrmpEntite> findByIdPrmpAndActifTrue(String idPrmp);

    /** Toutes les affectations d'une PRMP (actives ou non) — pour la lecture scopée (§3.1). */
    List<PrmpEntite> findByIdPrmp(String idPrmp);

    /** Vrai si un lien PRMP↔entité existe déjà (actif OU en attente) — dédup de l'auto-rattachement. */
    boolean existsByIdPrmpAndIdEntiteContract(String idPrmp, Integer idEntiteContract);

    /**
     * L'affectation active d'une entité, s'il y en a une. Sert à garantir l'invariant
     * « une seule PRMP active par entité » (§3.1).
     */
    Optional<PrmpEntite> findByIdEntiteContractAndActifTrue(Integer idEntiteContract);

    /** Plus grand ID_PRMP_ENTITE existant (0 si table vide) — pour générer la PK assignée. */
    @Query("select coalesce(max(p.idPrmpEntite), 0) from PrmpEntite p")
    Integer findMaxId();
}
