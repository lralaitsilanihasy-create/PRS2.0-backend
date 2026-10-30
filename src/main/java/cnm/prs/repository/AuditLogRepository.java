package cnm.prs.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.AuditLog;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    /** Prochaine PK allouee par la sequence serveur {@code seq_audit_log} (allocation atomique). */
    @Query(value = "select nextval('seq_audit_log')", nativeQuery = true)
    Long nextIdAuditLog();

    // ⚠️ 2026-10-30 (demande front « performance », §B4) — la recherche paginée et filtrée du journal passe par
    // AuditLogRecherche (une Specification : seuls les filtres présents entrent dans la requête), et non plus par une
    // requête unique aux filtres écrits « (:x = true or …) » / « coalesce(:debut, DATE_ACTION) », qui empêchaient
    // PostgreSQL d'utiliser les index de V153 et parcouraient toute la table à chaque page.

    /**
     * ⚠️ Lot 6 (2026-09-17, demande front §B3) — nombre d'écritures portées au nom d'un acteur depuis
     * un instant donné : le « … actions au journal » de la fiche d'annuaire.
     *
     * <p>{@code IM_ACTEUR} porte la <strong>référence</strong> de l'acteur ({@code IM_CONTROLEUR} ou
     * {@code ID_PRMP} / {@code ID_UGPM}, colonne {@code varchar(10)}), jamais son login : c'est bien
     * la référence de la fiche qu'on passe ici.</p>
     */
    @Query("select count(a) from AuditLog a where a.imActeur = :acteur and a.dateAction >= :depuis")
    long compterActionsDepuis(@Param("acteur") String acteur, @Param("depuis") LocalDateTime depuis);

    /** Rectifications PRMP d'un dossier (audit), par date croissante — pour l'historique d'échanges. */
    @Query("""
            select a from AuditLog a
            where a.typeAction = 'RECTIFICATION_PRMP' and a.idEnregistrement = :idEnr
            order by a.dateAction asc
            """)
    List<AuditLog> findRectificationsDossier(@Param("idEnr") String idEnregistrement);
}
