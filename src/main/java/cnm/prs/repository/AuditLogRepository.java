package cnm.prs.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.AuditLog;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** Prochaine PK allouee par la sequence serveur {@code seq_audit_log} (allocation atomique). */
    @Query(value = "select nextval('seq_audit_log')", nativeQuery = true)
    Long nextIdAuditLog();

    /**
     * ⚠️ Audit 2026-08-27 (lot D §4) — recherche <strong>paginée et filtrée en SQL</strong> du journal.
     *
     * <p>{@code t_audit_log} reçoit une ligne à chaque écriture de l'application : sa croissance est
     * monotone et sans fin. L'écran d'administration en demandait la <strong>totalité</strong>
     * ({@code findAll()}), ce qui revient, au bout de quelques mois d'exploitation, à télécharger des
     * années de journal pour en regarder les vingt dernières lignes.</p>
     *
     * <p>Chaque filtre est facultatif ({@code null} = pas de filtre) ; les bornes {@code debut} et
     * {@code fin} encadrent {@code DATE_ACTION} et sont toutes deux <strong>incluses</strong> (le
     * service passe en {@code fin} le dernier instant représentable du jour demandé). L'ordre est
     * celui du {@code Pageable} — le service impose {@code dateAction desc}.</p>
     *
     * <p>⚠️ Les bornes de date passent par {@code coalesce} et non par {@code :param is null} :
     * PostgreSQL refuse un paramètre nul dont il ne peut pas déduire le type
     * (« could not determine data type of parameter »), alors qu'il le déduit sans peine de l'autre
     * argument du {@code coalesce}. Comme {@code DATE_ACTION} est {@code NOT NULL}, une borne absente
     * se compare à la colonne elle-même — condition toujours vraie. Les filtres textuels gardent la
     * forme {@code :param is null}, qui leur convient (et qui, elle, ne peut pas être remplacée par
     * un {@code coalesce} : une colonne nulle serait alors exclue au lieu d'être retenue).</p>
     */
    @Query("""
            select a from AuditLog a
            where (:nomTable is null or a.nomTable = :nomTable)
              and (:acteur is null or a.imActeur = :acteur)
              and a.dateAction >= coalesce(:debut, a.dateAction)
              and a.dateAction <= coalesce(:fin, a.dateAction)
            """)
    Page<AuditLog> rechercher(@Param("nomTable") String nomTable, @Param("acteur") String acteur,
            @Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin, Pageable pageable);

    /** Rectifications PRMP d'un dossier (audit), par date croissante — pour l'historique d'échanges. */
    @Query("""
            select a from AuditLog a
            where a.typeAction = 'RECTIFICATION_PRMP' and a.idEnregistrement = :idEnr
            order by a.dateAction asc
            """)
    List<AuditLog> findRectificationsDossier(@Param("idEnr") String idEnregistrement);
}
