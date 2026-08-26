package cnm.prs.repository;

import java.util.List;

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

    /** Rectifications PRMP d'un dossier (audit), par date croissante — pour l'historique d'échanges. */
    @Query("""
            select a from AuditLog a
            where a.typeAction = 'RECTIFICATION_PRMP' and a.idEnregistrement = :idEnr
            order by a.dateAction asc
            """)
    List<AuditLog> findRectificationsDossier(@Param("idEnr") String idEnregistrement);
}
