package cnm.prs.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.MarchePrevision;

@Repository
public interface MarchePrevisionRepository extends JpaRepository<MarchePrevision, Integer> {

    /** Dates prévisionnelles d'un marché donné. */
    List<MarchePrevision> findByIdDetail(Integer idDetail);

    /** Dates prévisionnelles d'un marché, triées par l'ordre du processus ({@code t_capm.ORDRE}) ASC. */
    @Query("select p from MarchePrevision p left join fetch p.capm c where p.idDetail = :idDetail order by c.ordre asc")
    List<MarchePrevision> findByMarcheOrdonne(@Param("idDetail") Integer idDetail);

    /** Supprime les dates prévisionnelles d'un marché (cascade applicative à la suppression du marché). */
    long deleteByIdDetail(Integer idDetail);

    /** Plus grand ID_PREVISION existant (0 si table vide) — pour allouer la PK assignée à la saisie. */
    @Query("select coalesce(max(p.idPrevision), 0) from MarchePrevision p")
    Integer findMaxId();

    /**
     * ⚠️ LOT 3a (2026-08-26) — §1/§3.1 : dates prévisionnelles des dossiers du périmètre de l'appelant
     * (liste scopée), rattachées via la ligne de marché ({@code ID_DETAIL → t_marche.ID_DOSSIER}).
     */
    @Query("select p from MarchePrevision p, Marche m where m.idDetail = p.idDetail and m.idDossier in :idsDossiers")
    List<MarchePrevision> findParDossiers(@Param("idsDossiers") Collection<Integer> idsDossiers);

    /** Dossier rattaché à une prévision (via son marché) — contrôle de périmètre d'un accès unitaire. */
    @Query("select m.idDossier from MarchePrevision p, Marche m where p.idPrevision = :id and m.idDetail = p.idDetail")
    Optional<Integer> findIdDossier(@Param("id") Integer id);

    /** Vrai si la PK est déjà prise — la création réalloue alors la PK côté serveur (anti-collision). */
    boolean existsByIdPrevision(Integer idPrevision);
}
