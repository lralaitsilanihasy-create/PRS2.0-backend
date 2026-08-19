package cnm.prs.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import cnm.prs.entity.Actualite;

/**
 * Accès aux actualités ({@code t_actualite}) — spec du 2026-08-18.
 */
public interface ActualiteRepository extends JpaRepository<Actualite, Integer> {

    /**
     * Actualités visibles pour un profil (règle de visibilité, entièrement serveur) :
     * ACTIF + profil ciblé + fenêtre de dates (publication passée ou nulle, expiration non
     * atteinte ou nulle — « atteinte » = jour J compris). Le tri d'affichage est fait en Java
     * (date de publication effective décroissante).
     */
    @Query("""
            select a from Actualite a
            where a.statut = 'ACTIF'
              and exists (select 1 from ActualiteProfil p
                          where p.idActualite = a.idActualite and p.profil = :profil)
              and (a.datePublication is null or a.datePublication <= :aujourdHui)
              and (a.dateExpiration is null or a.dateExpiration > :aujourdHui)
            """)
    List<Actualite> visiblesPourProfil(@Param("profil") String profil,
            @Param("aujourdHui") LocalDate aujourdHui);

    /**
     * Bascule automatique « expiration = archivage » : toute actualité ACTIVE dont la date
     * d'expiration est atteinte passe ARCHIVE ({@code IM_ARCHIVEUR} reste null : archivage
     * système). Appelée au fil des lectures — pas de tâche planifiée à surveiller.
     * {@code flush/clear} : l'update JPQL contourne le cache de session — sans purge, une
     * lecture qui suit dans la même transaction resservirait l'entité au statut périmé.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Actualite a set a.statut = 'ARCHIVE', a.dateArchivage = :maintenant
            where a.statut = 'ACTIF' and a.dateExpiration is not null and a.dateExpiration <= :aujourdHui
            """)
    int archiverExpirees(@Param("aujourdHui") LocalDate aujourdHui,
            @Param("maintenant") LocalDateTime maintenant);
}
