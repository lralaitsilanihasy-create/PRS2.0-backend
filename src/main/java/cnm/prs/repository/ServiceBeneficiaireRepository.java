package cnm.prs.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.ServiceBeneficiaire;

@Repository
public interface ServiceBeneficiaireRepository extends JpaRepository<ServiceBeneficiaire, Integer> {

    /** Bénéficiaires d'un marché — copie d'une version à l'autre et comparaison de diff. */
    List<ServiceBeneficiaire> findByIdDetail(Integer idDetail);

    /** Supprime les bénéficiaires d'un marché (cascade applicative à la suppression du marché). */
    long deleteByIdDetail(Integer idDetail);

    /** Prochaine PK allouee par la sequence serveur {@code seq_service_beneficiaire} (allocation atomique). */
    @Query(value = "select nextval('seq_service_beneficiaire')", nativeQuery = true)
    Long nextIdBenef();

    /**
     * ⚠️ LOT 3a (2026-08-26) — §1/§3.1 : bénéficiaires des dossiers du périmètre de l'appelant
     * (liste scopée), rattachés via la ligne de marché ({@code ID_DETAIL → t_marche.ID_DOSSIER}).
     */
    @Query("select s from ServiceBeneficiaire s, Marche m where m.idDetail = s.idDetail and m.idDossier in :idsDossiers")
    List<ServiceBeneficiaire> findParDossiers(@Param("idsDossiers") Collection<Integer> idsDossiers);

    /** Dossier rattaché à un bénéficiaire (via son marché) — contrôle de périmètre d'un accès unitaire. */
    @Query("select m.idDossier from ServiceBeneficiaire s, Marche m where s.idBenef = :id and m.idDetail = s.idDetail")
    Optional<Integer> findIdDossier(@Param("id") Integer id);
}
