package cnm.prs.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.SessionDto;
import cnm.prs.entity.SessionUtilisateur;
import cnm.prs.repository.SessionUtilisateurRepository;

/**
 * ⚠️ Lot 6 (2026-09-17, demande front §B4) — <strong>lecture seule</strong> du journal des connexions.
 *
 * <p>Ce service n'expose <em>aucune</em> écriture, et c'est le cœur du besoin : la table était servie
 * par un CRUD générique ({@code /api/session-utilisateurs}), c'est-à-dire qu'un Administrateur pouvait
 * y créer, modifier et supprimer des traces de connexion. Un journal de preuve modifiable est pire
 * qu'absent — c'est le motif même pour lequel cet écran a quitté l'application. La table n'est
 * alimentée que par {@link JournalConnexionService}, sur le chemin du login et du logout.</p>
 */
@Service
@Transactional(readOnly = true)
public class SessionService {

    /** Le journal n'a qu'un ordre de lecture sensé : du plus récent au plus ancien. */
    private static final Sort PLUS_RECENT_DABORD = Sort.by(Sort.Direction.DESC, "dateConnexion");

    private final SessionUtilisateurRepository repository;

    public SessionService(SessionUtilisateurRepository repository) {
        this.repository = repository;
    }

    /**
     * Page de connexions, filtrée et triée du plus récent au plus ancien quel que soit le tri demandé
     * par le client.
     *
     * @param acteur référence d'acteur <strong>ou</strong> login tenté ; les deux, parce qu'un échec sur
     *               un login inconnu ne porte aucune référence d'acteur — filtrer sur la seule référence
     *               rendrait invisibles les lignes que l'on vient précisément regarder. Vide = pas de filtre
     * @param succes {@code true} = les connexions acceptées, {@code false} = les tentatives refusées ;
     *               {@code null} = les deux
     * @param du     premier jour inclus ; {@code null} = pas de borne inférieure
     * @param au     dernier jour <strong>inclus</strong> (la journée entière) ; {@code null} = pas de borne
     */
    public Page<SessionDto> rechercher(String acteur, Boolean succes, LocalDate du, LocalDate au,
            Pageable pageable) {
        Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), PLUS_RECENT_DABORD);
        return repository.rechercher(vide(acteur), succes,
                du == null ? null : du.atStartOfDay(),
                // Dernier instant représentable du jour (DATE_CONNEXION est un timestamp(6)) : la journée
                // demandée est incluse en entier, sans risque d'arrondi sur le lendemain. Même calcul que
                // AuditLogService.rechercher, pour que les deux journaux se filtrent pareil.
                au == null ? null : au.plusDays(1).atStartOfDay().minusNanos(1_000),
                page)
                .map(SessionService::toDto);
    }

    /**
     * La <strong>durée</strong> est calculée à la lecture et non stockée : elle se déduit des deux dates,
     * et une colonne de plus serait une occasion de plus qu'elles se contredisent.
     */
    private static SessionDto toDto(SessionUtilisateur s) {
        return new SessionDto(s.getImControleur(), s.getLogin(), s.getDateConnexion(),
                s.getDateDeconnexion(), dureeSecondes(s.getDateConnexion(), s.getDateDeconnexion()),
                s.getIpAdresse(), s.getUserAgent(), s.getSucces());
    }

    private static Long dureeSecondes(LocalDateTime debut, LocalDateTime fin) {
        return debut == null || fin == null ? null : Duration.between(debut, fin).toSeconds();
    }

    /** Un filtre vide vaut « pas de filtre » (le front envoie volontiers une chaîne vide). */
    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }
}
