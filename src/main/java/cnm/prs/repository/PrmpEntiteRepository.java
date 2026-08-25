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

    /**
     * Prochaine PK de rattachement PRMP↔entité, allouée par la séquence serveur (Voie B).
     *
     * <p>Remplace un {@code max(ID_PRMP_ENTITE) + 1}. À consommer <strong>une fois par ligne</strong> :
     * la validation d'une inscription en crée plusieurs d'affilée, et une valeur allouée une fois puis
     * incrémentée localement laisserait la séquence en retard — la validation suivante écraserait les
     * rattachements de la précédente ({@code save()} sur PK assignée = merge).
     */
    @Query(value = "select nextval('seq_prmp_entite')", nativeQuery = true)
    Long nextIdPrmpEntite();
}
