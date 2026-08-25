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

    /**
     * Prochaine PK de bénéficiaire, allouée par la séquence serveur (Voie B — l'id client est ignoré).
     *
     * <p>Remplace un {@code max(ID_BENEF) + 1}. À consommer <strong>une fois par ligne</strong> : la
     * ventilation d'un marché en crée plusieurs d'affilée, et une valeur allouée une fois puis
     * incrémentée localement laisserait la séquence en retard — la ventilation suivante écraserait
     * la précédente ({@code save()} sur PK assignée = merge).
     */
    @Query(value = "select nextval('seq_service_beneficiaire')", nativeQuery = true)
    Long nextIdBenef();
}
