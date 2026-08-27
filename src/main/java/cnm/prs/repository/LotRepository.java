package cnm.prs.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.Lot;

@Repository
public interface LotRepository extends JpaRepository<Lot, Integer> {

    /** Lots d'un marché (pour cascader leurs tranches avant suppression). */
    List<Lot> findByIdDetail(Integer idDetail);

    /** Lots d'un dossier (tous les lots de toutes ses lignes de marché). */
    List<Lot> findByIdDossier(Integer idDossier);

    /** Prochaine PK allouee par la sequence serveur {@code seq_lot} (allocation atomique). */
    @Query(value = "select nextval('seq_lot')", nativeQuery = true)
    Long nextIdLot();

    /** Supprime les lots d'un marché (cascade applicative — leurs tranches doivent être retirées avant). */
    long deleteByIdDetail(Integer idDetail);

    /**
     * Balayage de fermeture avant la suppression d'un dossier (⚠️ audit 2026-08-27, lot D §2) :
     * {@code t_lot} porte DEUX FK, {@code ID_DETAIL → t_marche} et {@code ID_DOSSIER → t_dossier}.
     * La cascade par marché ({@link #deleteByIdDetail}) ne ferme que la première ; ce balayage garantit
     * qu'aucun lot ne retient le dossier lui-même.
     */
    long deleteByIdDossier(Integer idDossier);

    /**
     * ⚠️ LOT 3a (2026-08-26) — §1/§3.1 : lots des dossiers du périmètre de l'appelant (liste scopée).
     * Les identifiants viennent de {@code PerimetreDossier} ; {@code t_lot} porte {@code ID_DOSSIER}.
     */
    List<Lot> findByIdDossierIn(Collection<Integer> idsDossiers);
}
