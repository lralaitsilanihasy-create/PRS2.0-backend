package cnm.prs.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.Mandat;

@Repository
public interface MandatRepository extends JpaRepository<Mandat, Integer> {

    /** Historique chronologique des mandats d'une PRMP (du plus ancien au plus récent). */
    List<Mandat> findByIdPrmpOrderByDateDebutAscIdMandatAsc(String idPrmp);

    /** Historique chronologique pour un ensemble de PRMP (lecture par UGPM / vue d'ensemble). */
    List<Mandat> findByIdPrmpInOrderByDateDebutAscIdMandatAsc(java.util.Collection<String> idsPrmp);

    /** Tous les mandats, chronologiques (vue Administrateur). */
    List<Mandat> findAllByOrderByDateDebutAscIdMandatAsc();

    /** Nombre de mandats déjà portés par une personne — base de la garde de renouvellement unique. */
    long countByIdPrmp(String idPrmp);

    /** Un arrêté ne sert qu'une fois : une reconduction exige un nouvel arrêté. */
    boolean existsByRefArreteIgnoreCase(String refArrete);

    /**
     * Mandat <strong>en vigueur</strong> d'une PRMP à une date donnée : encadrant la date et non abrogé.
     * Au plus un (garanti par le contrôle de chevauchement à la création).
     */
    @Query("""
            select m from Mandat m
            where m.idPrmp = :idPrmp
              and m.dateAbrogation is null
              and m.dateDebut <= :date
              and m.dateFin >= :date
            order by m.dateDebut desc
            """)
    List<Mandat> findEnVigueur(@Param("idPrmp") String idPrmp, @Param("date") LocalDate date);

    /** Dernier mandat déclaré d'une PRMP (le plus récent par date de début). */
    Optional<Mandat> findFirstByIdPrmpOrderByDateDebutDescIdMandatDesc(String idPrmp);

    /**
     * Mandats d'une PRMP dont la période <strong>chevauche</strong> [debut, fin] (abrogés exclus) —
     * deux mandats d'une même personne ne peuvent pas se recouvrir.
     */
    @Query("""
            select m from Mandat m
            where m.idPrmp = :idPrmp
              and m.dateAbrogation is null
              and m.dateDebut <= :fin
              and m.dateFin >= :debut
            """)
    List<Mandat> findChevauchants(@Param("idPrmp") String idPrmp,
            @Param("debut") LocalDate debut, @Param("fin") LocalDate fin);
}
