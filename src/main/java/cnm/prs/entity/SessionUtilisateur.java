package cnm.prs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Journal des connexions (table {@code t_session_utilisateur}) : une ligne par <strong>tentative</strong>
 * de connexion, réussie ou non, fermée au {@code logout}.
 *
 * <p>⚠️ Lot 6 (2026-09-17, demande front §B4, migration {@code V31}) — la table existait depuis le MLD
 * mais <strong>aucun code applicatif ne l'écrivait</strong> : le seul {@code new SessionUtilisateur()} du
 * backend était celui du mapper du CRUD générique, c'est-à-dire l'écran d'administration lui-même. Elle
 * est désormais alimentée par {@link cnm.prs.service.JournalConnexionService}, et le CRUD qui permettait
 * d'y forger une fausse trace a été retiré au profit de la seule lecture {@code GET /api/sessions}.</p>
 */
@Entity
@Table(name = "t_session_utilisateur")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SessionUtilisateur {

    /**
     * Identifiant de session. Pour une connexion réussie, c'est l'<strong>empreinte SHA-256 du jeton</strong>
     * émis (voir {@code JournalConnexionService.empreinte}) : le {@code logout} la recalcule depuis le
     * cookie {@code PRS_SESSION} et retrouve ainsi sa ligne sans que le jeton lui-même soit stocké. Pour un
     * échec, un UUID — il n'y a pas de jeton.
     */
    @Id
    @Column(name = "ID_SESSION", nullable = false, length = 100)
    private String idSession;

    /**
     * ⚠️ V31 — <strong>référence de n'importe quel acteur</strong> : {@code IM_CONTROLEUR}
     * ({@code tr_controleur}), {@code ID_PRMP} ({@code t_prmp}) ou {@code ID_UGPM} ({@code t_ugpm}).
     *
     * <p><strong>Le nom de la colonne est historique et trompeur</strong>, et il est conservé tel quel :
     * jusqu'à V31 elle était en {@code varchar(7)} et portait une clé étrangère vers {@code tr_controleur},
     * si bien qu'aucune connexion de PRMP ni d'UGPM n'était insérable (un {@code ID_PRMP} fait 10
     * caractères). V31 l'élargit à 10 et <strong>retire la clé étrangère</strong>, qui ne pouvait plus
     * valoir pour tous les acteurs. Le renommer aurait coûté l'entité, l'index de V8 et le nettoyage de
     * {@code ControleurService} pour un gain cosmétique ; le sens réel est écrit ici, dans le
     * {@code COMMENT} de la colonne et dans {@code docs/api-endpoints.md}.</p>
     *
     * <p>{@code null} quand la tentative a échoué sur un login inconnu : il n'existe alors aucun acteur à
     * désigner — seul {@link #login} porte l'information.</p>
     */
    @Column(name = "IM_CONTROLEUR", length = 10)
    private String imControleur;

    /**
     * ⚠️ V31 — identifiant <strong>tenté</strong>, renseigné à chaque tentative, réussie ou non. C'est la
     * seule trace exploitable d'un échec sur un login inconnu ({@link #imControleur} est alors nul) :
     * « la ligne qui manque le plus », selon la demande §B4.
     */
    @Column(name = "LOGIN", length = 100)
    private String login;

    @Column(name = "DATE_CONNEXION")
    private LocalDateTime dateConnexion;

    /** Renseignée au {@code POST /api/auth/logout} ; nulle tant que la session n'a pas été fermée. */
    @Column(name = "DATE_DECONNEXION")
    private LocalDateTime dateDeconnexion;

    @Column(name = "IP_ADRESSE", length = 45)
    private String ipAdresse;

    @Column(name = "USER_AGENT", length = 300)
    private String userAgent;

    /** Vrai si les identifiants ont été acceptés ; faux pour une tentative refusée. */
    @Column(name = "SUCCES")
    private Boolean succes;
}
