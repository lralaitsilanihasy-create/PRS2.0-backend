package cnm.prs.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.Tranche;

@Repository
public interface TrancheRepository extends JpaRepository<Tranche, Integer> {

    /** Supprime les tranches des lots donnés (cascade applicative à la suppression du marché). */
    long deleteByIdLotIn(Collection<Integer> idLots);

    /**
     * ⚠️ LOT 3a (2026-08-26) — §1/§3.1 : tranches des dossiers du périmètre de l'appelant (liste scopée).
     * Le rattachement passe par le lot ({@code t_tranche.ID_LOT → t_lot.ID_DOSSIER}).
     */
    @Query("select t from Tranche t, Lot l where l.idLot = t.idLot and l.idDossier in :idsDossiers")
    List<Tranche> findParDossiers(@Param("idsDossiers") Collection<Integer> idsDossiers);

    /** Dossier rattaché à une tranche (via son lot) — contrôle de périmètre d'un accès unitaire. */
    @Query("select l.idDossier from Tranche t, Lot l where t.idTranche = :id and l.idLot = t.idLot")
    Optional<Integer> findIdDossier(@Param("id") Integer id);

    /** Dossier rattaché à un lot — contrôle de périmètre avant écriture d'une tranche. */
    @Query("select l.idDossier from Lot l where l.idLot = :idLot")
    Optional<Integer> findIdDossierParLot(@Param("idLot") Integer idLot);
}
