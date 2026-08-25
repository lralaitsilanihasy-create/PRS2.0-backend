package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.Notification;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Integer> {

    /**
     * Prochaine PK de notification, allouée par la séquence serveur (Voie B — même motif que
     * {@code seq_dossier} / {@code seq_marche}).
     *
     * <p>Remplace un {@code max(ID_NOTIFICATION) + 1}. Une notification est presque toujours émise
     * <strong>dans la transaction métier de l'appelant</strong> (validation, dispatch, rectification…)
     * et le projet ne pose aucun {@code REQUIRES_NEW} : deux transitions simultanées lisaient le même
     * maximum, et la violation d'unicité de la seconde annulait l'acte métier lui-même, pas seulement
     * son avis. {@code nextval} est atomique et hors transaction.
     */
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
}
