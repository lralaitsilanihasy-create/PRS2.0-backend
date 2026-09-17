package cnm.prs.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import cnm.prs.entity.SessionUtilisateur;
import cnm.prs.repository.CompteAuthRepository;
import cnm.prs.repository.SessionUtilisateurRepository;

/**
 * ⚠️ Lot 6 (2026-09-17, demande front §B4) — <strong>journal des connexions</strong> : une ligne de
 * {@code t_session_utilisateur} par tentative de connexion, réussie ou non, fermée au {@code logout}.
 *
 * <h2>Pourquoi cette table plutôt que le journal d'audit</h2>
 * Trois raisons, arrêtées avec le front : une ligne d'audit décrit une <em>écriture</em> (table,
 * enregistrement, champ) là où une session décrit une <em>durée</em> ; {@code AuditInterceptor} ignore
 * tout ce qui renvoie ≥ 400, donc les échecs — précisément ce qu'on veut voir — seraient perdus ; et la
 * table comme sa clé étrangère depuis {@code t_audit_log.SESSION_ID} existent depuis la baseline, il n'y
 * avait rien à concevoir. Voir {@code docs/adr/ADR-0005-journal-des-connexions.md}.
 *
 * <h2>L'identifiant de session est l'empreinte du jeton</h2>
 * Pour une connexion réussie, {@code ID_SESSION} est le SHA-256 du JWT émis ({@link #empreinte}). Le
 * {@code logout} le recalcule depuis le cookie {@code PRS_SESSION} (ou l'en-tête {@code Bearer}) et
 * retrouve ainsi <em>sa</em> ligne — sans qu'aucun jeton ne soit stocké en base, et sans toucher au
 * contrat du JWT : un claim {@code jti} aurait exigé de faire remonter l'identifiant à travers
 * {@code AuthService} et {@code LoginResponse}, donc jusqu'au client. Un échec n'a pas de jeton : son
 * identifiant est un UUID.
 *
 * <h2>Le journal ne casse jamais la connexion</h2>
 * Principe déjà retenu par {@code AuditInterceptor} (« l'audit ne doit jamais casser la requête »), et
 * plus impératif encore ici : une panne d'écriture rendrait l'application inconnectable.
 * <ul>
 *   <li><strong>Prévention</strong> — tout ce qui vient du client (login tenté, adresse, agent) est
 *       tronqué à la longueur de sa colonne, au plus près de celle-ci. Un agent de plus de 300
 *       caractères, ou un {@code X-Forwarded-For} forgé, sont des entrées <em>ordinaires</em>, pas des
 *       incidents : les laisser atteindre PostgreSQL les transformerait en 22001 à chaque connexion.</li>
 *   <li><strong>Dernier recours</strong> — chaque méthode publique avale ce qui remonterait et le
 *       consigne en {@code WARN}. Le code de retour et le corps de la réponse sont inchangés.</li>
 *   <li><strong>Pas de transaction propre</strong> — ce service n'est <em>pas</em> {@code @Transactional} :
 *       il s'appuie sur celle que le repository ouvre pour lui. Un {@code REQUIRES_NEW} aurait paru plus
 *       sûr, mais il aurait aussi <em>échappé au rollback des tests</em> et laissé des lignes derrière
 *       chaque exécution de la suite ; et en exploitation il n'y a de toute façon aucune transaction en
 *       cours à cet instant — {@code AuthService.login} est terminé quand on arrive ici.</li>
 * </ul>
 *
 * <h2>Volume</h2>
 * Une ligne par tentative, échecs compris, <strong>sauf les refus du quota</strong>
 * ({@code LoginRateLimiter} → 429) : une tentative refusée avant tout examen des identifiants n'est pas
 * une tentative de connexion, et la journaliser aurait retiré au verrou son effet de plafond — c'est
 * pendant le verrou qu'un attaquant frappe le plus. Le débit d'échecs journalisables est donc borné par
 * le limiteur : 20 par adresse et par quart d'heure, soit au plus 1 920 lignes par jour et par adresse.
 * <strong>Aucune purge n'est implémentée</strong> : la rétention d'un journal de preuve est une décision
 * produit.
 */
@Service
public class JournalConnexionService {

    private static final Logger log = LoggerFactory.getLogger(JournalConnexionService.class);

    /** Longueurs des colonnes alimentées ici (V1 pour les trois premières, V31 pour {@code LOGIN}). */
    private static final int LONGUEUR_LOGIN = 100;
    private static final int LONGUEUR_IP = 45;
    private static final int LONGUEUR_USER_AGENT = 300;

    private final SessionUtilisateurRepository repository;
    private final CompteAuthRepository compteRepository;

    public JournalConnexionService(SessionUtilisateurRepository repository,
            CompteAuthRepository compteRepository) {
        this.repository = repository;
        this.compteRepository = compteRepository;
    }

    /**
     * Connexion acceptée : ligne neuve identifiée par l'empreinte du jeton, avec la référence de
     * l'acteur, l'horodatage, l'adresse et l'agent.
     *
     * <p>La référence écrite est celle du compte ({@code REF_ACTEUR}), c'est-à-dire <strong>la personne
     * qui s'est connectée</strong> — pour une UGPM son propre identifiant, et non celui de sa PRMP de
     * tutelle que porte {@code LoginResponse.ref()} : ce dernier est un périmètre de visibilité, pas une
     * identité, et la fiche d'annuaire se cherche par identité.</p>
     */
    public void connexionReussie(String login, String jeton, String ip, String userAgent) {
        try {
            SessionUtilisateur session = new SessionUtilisateur();
            session.setIdSession(empreinte(jeton));
            session.setLogin(tronquer(login, LONGUEUR_LOGIN));
            session.setImControleur(refActeurDe(login));
            session.setDateConnexion(LocalDateTime.now());
            session.setIpAdresse(tronquer(ip, LONGUEUR_IP));
            session.setUserAgent(tronquer(userAgent, LONGUEUR_USER_AGENT));
            session.setSucces(true);
            repository.save(session);
        } catch (Exception e) {
            log.warn("Echec de la journalisation de la connexion de {} : {}", login, e.getMessage());
        }
    }

    /**
     * Connexion refusée : ligne neuve avec {@code SUCCES = false} et l'identifiant <strong>tenté</strong>.
     * C'est la ligne qui manquait le plus — jusqu'ici les échecs ne vivaient que dans les tables en
     * mémoire de {@code LoginRateLimiter}, effacées à chaque redémarrage.
     *
     * <p>La référence d'acteur est renseignée quand le login existe (mot de passe faux, compte fermé) et
     * laissée nulle quand il est inconnu : il n'y a alors personne à désigner. Les deux cas se lisent
     * dans le journal — « quelqu'un s'acharne sur le compte de X » et « quelqu'un essaie des noms au
     * hasard » ne sont pas la même alerte.</p>
     */
    public void connexionRefusee(String login, String ip, String userAgent) {
        try {
            SessionUtilisateur session = new SessionUtilisateur();
            session.setIdSession(UUID.randomUUID().toString());
            session.setLogin(tronquer(login, LONGUEUR_LOGIN));
            session.setImControleur(refActeurDe(login));
            session.setDateConnexion(LocalDateTime.now());
            session.setIpAdresse(tronquer(ip, LONGUEUR_IP));
            session.setUserAgent(tronquer(userAgent, LONGUEUR_USER_AGENT));
            session.setSucces(false);
            repository.save(session);
        } catch (Exception e) {
            log.warn("Echec de la journalisation d'une tentative refusee : {}", e.getMessage());
        }
    }

    /**
     * Déconnexion : date de fin posée sur la session du jeton présenté. Sans jeton exploitable (session
     * déjà expirée, appel anonyme du {@code logout}), il n'y a rien à fermer et rien à signaler — le
     * {@code logout} reste une route publique, appelable même sans session.
     *
     * @return {@code true} si une session ouverte a bien été fermée
     */
    public boolean deconnexion(String jeton) {
        if (jeton == null || jeton.isBlank()) {
            return false;
        }
        try {
            return repository.fermer(empreinte(jeton), LocalDateTime.now()) > 0;
        } catch (Exception e) {
            log.warn("Echec de la journalisation d'une deconnexion : {}", e.getMessage());
            return false;
        }
    }

    /**
     * Empreinte SHA-256 (hexadécimal, 64 caractères) du jeton : c'est l'{@code ID_SESSION} d'une
     * connexion réussie. Déterministe — le {@code logout} la recalcule — et non réversible : une fuite
     * de la base ne rend aucun jeton.
     */
    public static String empreinte(String jeton) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(jeton.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);   // inatteignable sur une JVM conforme
        }
    }

    /** Référence de l'acteur derrière ce login, ou {@code null} si le login est inconnu. */
    private String refActeurDe(String login) {
        if (login == null || login.isBlank()) {
            return null;
        }
        return compteRepository.findByLogin(login).map(c -> c.getRefActeur()).orElse(null);
    }

    private static String tronquer(String valeur, int max) {
        return valeur == null || valeur.length() <= max ? valeur : valeur.substring(0, max);
    }
}
