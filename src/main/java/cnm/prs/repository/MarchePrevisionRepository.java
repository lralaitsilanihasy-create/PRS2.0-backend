package cnm.prs.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.MarchePrevision;

@Repository
public interface MarchePrevisionRepository extends JpaRepository<MarchePrevision, Integer> {

    /** Dates prévisionnelles d'un marché donné. */
    List<MarchePrevision> findByIdDetail(Integer idDetail);

    /** Dates prévisionnelles d'un ensemble de marchés — scoping de la liste sur le périmètre du parent. */
    List<MarchePrevision> findByIdDetailIn(Collection<Integer> idDetails);

    /** Dates prévisionnelles d'un marché, triées par l'ordre du processus ({@code t_capm.ORDRE}) ASC. */
    @Query("select p from MarchePrevision p left join fetch p.capm c where p.idDetail = :idDetail order by c.ordre asc")
    List<MarchePrevision> findByMarcheOrdonne(@Param("idDetail") Integer idDetail);

    /** Supprime les dates prévisionnelles d'un marché (cascade applicative à la suppression du marché). */
    long deleteByIdDetail(Integer idDetail);

    /**
     * Prochaine PK de date prévisionnelle, allouée par la séquence serveur (Voie B — id client ignoré).
     *
     * <p>Remplace un {@code max(ID_PREVISION) + 1}. À consommer <strong>une fois par ligne</strong> :
     * la saisie d'un PPM en crée une par processus, et allouer une seule valeur puis l'incrémenter
     * localement laisserait la séquence en retard — la saisie suivante réattribuerait les mêmes
     * identifiants et écraserait les prévisions précédentes ({@code save()} sur PK assignée = merge).
     */
    @Query(value = "select nextval('seq_marche_prevision')", nativeQuery = true)
    Long nextIdMarchePrevision();
}
