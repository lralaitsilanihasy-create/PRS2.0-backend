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

    /** Lots d'un ensemble de marchés — support du scoping de la liste sur le périmètre du marché parent. */
    List<Lot> findByIdDetailIn(Collection<Integer> idDetails);

    /**
     * Prochaine PK de lot, allouée par la séquence serveur (Voie B — l'id client est ignoré).
     *
     * <p>Remplace un {@code max(ID_LOT) + 1} : deux saisies concurrentes lisaient le même maximum et
     * la seconde échouait en violation d'unicité. À consommer <strong>une fois par ligne</strong> —
     * allouer une valeur puis l'incrémenter localement laisse la séquence en retard, et la création
     * suivante réattribue les mêmes identifiants (sur PK assignée, {@code save()} est un merge : elle
     * écraserait les lignes précédentes au lieu de les compléter).
     */
    @Query(value = "select nextval('seq_lot')", nativeQuery = true)
    Long nextIdLot();

    /** Supprime les lots d'un marché (cascade applicative — leurs tranches doivent être retirées avant). */
    long deleteByIdDetail(Integer idDetail);
}
