package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.Notification;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Integer> {

    /** Prochaine PK allouee par la sequence serveur {@code seq_notification} (allocation atomique). */
    @Query(value = "select nextval('seq_notification')", nativeQuery = true)
    Long nextIdNotification();

    /** Notifications d'un contrôleur (clé unifiée {@code ref}+{@code type}), plus récentes d'abord. */
    @Query("""
            select n from Notification n
            where n.destinataireRef = :ref and n.destinataireType = 'CONTROLEUR'
            order by n.dateEnvoi desc
            """)
    List<Notification> findPourControleur(@Param("ref") String ref);

    /**
     * Notifications d'une PRMP : par clé {@code ref}+{@code type}, avec repli sur l'e-mail
     * pour les notifications antérieures à l'unification (non enrichies).
     */
    @Query("""
            select n from Notification n
            where (n.destinataireRef = :ref and n.destinataireType = 'PRMP')
               or (:email is not null and n.destinataireEmail = :email)
            order by n.dateEnvoi desc
            """)
    List<Notification> findPourPrmp(@Param("ref") String ref, @Param("email") String email);

    /** Supprime les notifications d'un dossier (cascade à la suppression du dossier brouillon). */
    void deleteByIdDossier(Integer idDossier);

    /** ⚠️ V67 (2026-10-04, lot 2a) — les notifications d'un destinataire d'un type donné ({@code MEMBRE_CAO}), récentes d'abord. */
    @Query("""
            select n from Notification n
            where n.destinataireRef = :ref and n.destinataireType = :type
            order by n.dateEnvoi desc
            """)
    List<Notification> findPourRefEtType(@Param("ref") String ref, @Param("type") String type);

    /** ⚠️ V66 (2026-10-04, lot 2, §B4) — un rappel du même type déjà émis vers ce destinataire pour cet objet depuis une date. */
    boolean existsByTypeNotifAndDestinataireRefAndTypeObjetAndIdObjetAndDateEnvoiGreaterThanEqual(String typeNotif,
            String destinataireRef, String typeObjet, Integer idObjet, java.time.LocalDateTime depuis);
}
