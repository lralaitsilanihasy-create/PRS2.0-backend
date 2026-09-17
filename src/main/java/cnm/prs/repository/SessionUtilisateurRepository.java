package cnm.prs.repository;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.SessionUtilisateur;

@Repository
public interface SessionUtilisateurRepository extends JpaRepository<SessionUtilisateur, String> {

    /** Supprime les sessions d'un contrôleur (nettoyage à la suppression du contrôleur). */
    long deleteByImControleur(String imControleur);

    /**
     * ⚠️ Lot 6 (2026-09-17, §B4) — ferme la session du jeton présenté au {@code logout}, en une seule
     * instruction : lire puis écrire aurait laissé une fenêtre entre les deux, et deux onglets qui se
     * déconnectent en même temps y auraient écrasé la date l'un de l'autre.
     *
     * <p>La condition {@code dateDeconnexion is null} rend l'opération <strong>idempotente</strong> : un
     * second {@code logout} avec le même jeton ne rajeunit pas la fermeture déjà enregistrée. Le compte
     * de lignes touchées (0 ou 1) dit si une session a réellement été fermée.</p>
     *
     * <p>{@code @Transactional} porté ici : le service appelant ne l'est pas — voir la javadoc de
     * {@code JournalConnexionService} pour la raison. {@code flushAutomatically} est indispensable —
     * une mise à jour JPQL court-circuite le contexte de persistance : sans lui, l'insertion du login
     * encore en attente dans la même transaction serait écrite <em>après</em> cet {@code update}, qui ne
     * trouverait rien à fermer. {@code clearAutomatically} évite ensuite de relire l'entité périmée.</p>
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("""
            update SessionUtilisateur s set s.dateDeconnexion = :quand
            where s.idSession = :idSession and s.dateDeconnexion is null
            """)
    int fermer(@Param("idSession") String idSession, @Param("quand") LocalDateTime quand);

    /**
     * ⚠️ Lot 6 (2026-09-17, §B4) — compteur {@code sessionsOuvertes} de l'accueil : connexions réussies
     * jamais fermées et <strong>récentes</strong>.
     *
     * <p>Le seuil est indispensable : une session n'est fermée que par un {@code logout} explicite, or
     * la plupart des utilisateurs ferment simplement leur onglet. Sans borne, le compteur ne
     * redescendrait jamais et mesurerait l'historique des connexions, pas les sessions vivantes.</p>
     */
    @Query("""
            select count(s) from SessionUtilisateur s
            where s.succes = true and s.dateDeconnexion is null and s.dateConnexion >= :depuis
            """)
    long compterOuvertes(@Param("depuis") LocalDateTime depuis);

    /** ⚠️ Lot 6 (2026-09-17, §B4) — compteur {@code echecsConnexion24h} de l'accueil. */
    @Query("""
            select count(s) from SessionUtilisateur s
            where s.succes = false and s.dateConnexion >= :depuis
            """)
    long compterEchecsDepuis(@Param("depuis") LocalDateTime depuis);

    /**
     * ⚠️ Lot 6 (2026-09-17, §B4) — journal paginé de {@code GET /api/sessions}. Tous les filtres sont
     * facultatifs et se cumulent ; le tri (date de connexion décroissante) est imposé par le service.
     *
     * <p>Les bornes de date passent par {@code coalesce} et non par {@code :param is null}, comme dans
     * {@code AuditLogRepository.rechercher} : PostgreSQL refuse un paramètre nul dont il ne peut pas
     * déduire le type. {@code DATE_CONNEXION} pouvant être nulle pour les lignes d'avant V31, la
     * comparaison à elle-même laisse ces lignes passer, ce qui est le sens voulu de « pas de borne ».</p>
     *
     * @param acteur référence d'acteur <em>ou</em> login tenté — les deux, parce qu'un échec sur un
     *               login inconnu ne porte pas de référence d'acteur ; sans casse sur le login
     */
    @Query("""
            select s from SessionUtilisateur s
            where (:acteur is null or s.imControleur = :acteur or lower(s.login) = lower(:acteur))
              and (:succes is null or s.succes = :succes)
              and s.dateConnexion >= coalesce(:debut, s.dateConnexion)
              and s.dateConnexion <= coalesce(:fin, s.dateConnexion)
            """)
    Page<SessionUtilisateur> rechercher(@Param("acteur") String acteur,
            @Param("succes") Boolean succes,
            @Param("debut") LocalDateTime debut,
            @Param("fin") LocalDateTime fin,
            Pageable pageable);
}
