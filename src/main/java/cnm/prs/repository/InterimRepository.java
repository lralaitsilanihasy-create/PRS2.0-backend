package cnm.prs.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.Interim;

/**
 * Intérims désignés ({@code t_interim}, V34). Toutes les lectures « à une date » appliquent la même
 * définition de l'intérim <strong>en vigueur</strong> : {@code dateDebut ≤ date}, {@code dateFin} nulle ou
 * {@code ≥ date}, et révocation absente ou postérieure à la date — celle de
 * {@code InterimService#statutEffectif}, en JPQL.
 */
@Repository
public interface InterimRepository extends JpaRepository<Interim, Integer> {

    /** Historique chronologique complet (Administrateur, contrôleurs). */
    List<Interim> findAllByOrderByDateDebutAscIdInterimAsc();

    List<Interim> findByImTitulaireOrderByDateDebutAscIdInterimAsc(String imTitulaire);

    List<Interim> findByImInterimaireOrderByDateDebutAscIdInterimAsc(String imInterimaire);

    /** Intérims en vigueur à une date où {@code im} est l'<strong>intérimaire</strong> (ses suppléances). */
    @Query("""
            select i from Interim i
            where i.imInterimaire = :im
              and i.dateDebut <= :date
              and (i.dateFin is null or i.dateFin >= :date)
              and (i.dateRevocation is null or i.dateRevocation > :date)
            order by i.dateDebut asc, i.idInterim asc
            """)
    List<Interim> findEnVigueurPourInterimaire(@Param("im") String im, @Param("date") LocalDate date);

    /** Intérims en vigueur à une date où {@code im} est le <strong>titulaire</strong> (au plus un, garanti à la création). */
    @Query("""
            select i from Interim i
            where i.imTitulaire = :im
              and i.dateDebut <= :date
              and (i.dateFin is null or i.dateFin >= :date)
              and (i.dateRevocation is null or i.dateRevocation > :date)
            order by i.dateDebut asc, i.idInterim asc
            """)
    List<Interim> findEnVigueurPourTitulaire(@Param("im") String im, @Param("date") LocalDate date);

    /** Tous les intérims en vigueur à une date (annuaire des contrôleurs : une requête pour toute la liste). */
    @Query("""
            select i from Interim i
            where i.dateDebut <= :date
              and (i.dateFin is null or i.dateFin >= :date)
              and (i.dateRevocation is null or i.dateRevocation > :date)
            order by i.dateDebut asc, i.idInterim asc
            """)
    List<Interim> findEnVigueur(@Param("date") LocalDate date);

    /**
     * Intérims <strong>en cours ou à venir</strong> à une date ({@code ?actifs=true}) : pas encore achevés,
     * révocation absente ou postérieure. Le statut A_VENIR / ACTIF est ensuite dérivé ligne par ligne.
     */
    @Query("""
            select i from Interim i
            where (i.dateFin is null or i.dateFin >= :date)
              and (i.dateRevocation is null or i.dateRevocation > :date)
            order by i.dateDebut asc, i.idInterim asc
            """)
    List<Interim> findEnCoursOuAVenir(@Param("date") LocalDate date);

    /**
     * Intérims d'un titulaire dont la période <strong>chevauche</strong> [debut, fin] — un seul intérim ACTIF ou
     * A_VENIR par titulaire à une date donnée (409 sinon). Un intérim révoqué avant {@code debut} ne compte
     * plus ; un intérim sans fin ({@code VACANCE_POSTE}) chevauche tout ce qui commence après lui.
     *
     * @param fin borne haute de la période demandée ; l'appelant passe une date lointaine pour « sans fin »
     */
    @Query("""
            select i from Interim i
            where i.imTitulaire = :imTitulaire
              and i.dateDebut <= :fin
              and (i.dateFin is null or i.dateFin >= :debut)
              and (i.dateRevocation is null or i.dateRevocation > :debut)
            """)
    List<Interim> findChevauchantsTitulaire(@Param("imTitulaire") String imTitulaire,
            @Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    /** Même chevauchement, côté <strong>intérimaire</strong> : le cumul est signalé, pas interdit (§B3). */
    @Query("""
            select i from Interim i
            where i.imInterimaire = :imInterimaire
              and i.dateDebut <= :fin
              and (i.dateFin is null or i.dateFin >= :debut)
              and (i.dateRevocation is null or i.dateRevocation > :debut)
            """)
    List<Interim> findChevauchantsInterimaire(@Param("imInterimaire") String imInterimaire,
            @Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    /** Intérims (toute période) où {@code im} est titulaire ou intérimaire — pour {@code /mes}. */
    @Query("""
            select i from Interim i
            where i.imTitulaire = :im or i.imInterimaire = :im
            order by i.dateDebut asc, i.idInterim asc
            """)
    List<Interim> findConcernant(@Param("im") String im);

    /** Intérims par identifiants (résolution en lot des libellés d'une liste de traces). */
    List<Interim> findByIdInterimIn(Collection<Integer> ids);
}
