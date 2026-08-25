package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.AuditLog;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Prochaine PK du journal, allouée par la séquence serveur (Voie B — même motif que
     * {@code seq_dossier} / {@code seq_marche}).
     *
     * <p>Remplace un {@code max(ID_LOG) + 1}. Le journal est écrit <strong>dans la transaction
     * métier de l'appelant</strong> (le projet ne pose aucun {@code REQUIRES_NEW}) : deux écritures
     * concurrentes lisaient le même maximum, inséraient la même PK, et la violation d'unicité de la
     * seconde annulait toute la transaction métier — le dossier n'était pas validé, et l'utilisateur
     * recevait un message de doublon qui ne décrivait pas son action. {@code nextval} est atomique et
     * hors transaction : deux appelants n'obtiennent jamais la même valeur.
     */
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
