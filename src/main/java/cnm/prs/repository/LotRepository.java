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

    /** Plus grand ID_LOT existant (0 si vide) — PK allouée serveur (Voie B). */
    @Query("select coalesce(max(l.idLot), 0) from Lot l")
    Integer findMaxIdLot();

    /** Supprime les lots d'un marché (cascade applicative — leurs tranches doivent être retirées avant). */
    long deleteByIdDetail(Integer idDetail);

    /**
     * ⚠️ LOT 3a (2026-08-26) — §1/§3.1 : lots des dossiers du périmètre de l'appelant (liste scopée).
     * Les identifiants viennent de {@code PerimetreDossier} ; {@code t_lot} porte {@code ID_DOSSIER}.
     */
    List<Lot> findByIdDossierIn(Collection<Integer> idsDossiers);
}
