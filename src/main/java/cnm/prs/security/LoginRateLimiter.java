package cnm.prs.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import cnm.prs.exception.TropDeRequetesException;

/**
 * ⚠️ Audit 2026-08-27 (lot E) — limitation de débit des routes <strong>publiques</strong> de
 * {@code /api/auth} : {@code POST /api/auth/login} n'avait aucun compteur, aucun délai, aucun
 * verrou. Un attaquant pouvait essayer les mots de passe d'un compte connu aussi vite que le
 * réseau le permettait, sans jamais être ralenti — le seul frein était le coût de BCrypt.
 * Modèle repris du dépôt Collegue ({@code LoginRateLimiter}), adapté à nos conventions.
 *
 * <p><b>Trois compteurs</b>, tous en <strong>fenêtre glissante</strong> : on ne compte que les
 * évènements encore dans la fenêtre, rien n'est remis à zéro d'un coup à heure fixe (une remise à
 * zéro périodique offrirait, à chaque bascule, une rafale gratuite).</p>
 * <ul>
 *   <li><b>Couple (IP, login)</b> — {@value #ECHECS_MAX_COMPTE} échecs en 15 minutes verrouillent
 *       cet identifiant <em>vu de cette adresse</em>. C'est le verrou anti-bruteforce : il vise UN
 *       compte. Volontairement porté par le couple et non par le seul login, pour qu'un tiers ne
 *       puisse pas verrouiller le compte d'autrui depuis sa propre adresse — le verrou de compte
 *       « pur » est lui-même une arme de déni de service contre un utilisateur légitime.</li>
 *   <li><b>IP seule</b> — {@value #ECHECS_MAX_IP} échecs en 15 minutes, toutes identités
 *       confondues, verrouillent l'adresse. C'est le verrou anti-<em>password spraying</em> : un
 *       seul mot de passe essayé sur des centaines de logins ne déclenche jamais le verrou de
 *       compte (un échec par compte), mais fait exploser celui-ci.</li>
 *   <li><b>Inscriptions par IP</b> — {@value #INSCRIPTIONS_MAX_IP} demandes par heure.
 *       {@code /api/auth/register/**} est public, crée des fiches et <strong>stocke des
 *       fichiers</strong> (arrêté, CIN, photo, jusqu'à 25 Mo par requête) : sans quota, une simple
 *       boucle remplit la base et le disque. Le quota est volontairement large — une organisation
 *       derrière un même NAT peut inscrire plusieurs agents le même jour — mais borne le débit.</li>
 * </ul>
 *
 * <p><b>Ce qui déverrouille</b> : le temps, ou une connexion réussie ({@link #succesLogin}) qui
 * efface le compteur du couple (IP, login). Jamais celui de l'IP : sinon un attaquant disposant
 * d'un compte valide remettrait à zéro son propre quota d'adresse à volonté. Une requête
 * <strong>refusée</strong> pendant un verrou n'enregistre aucun échec — le verrou ne s'auto-prolonge
 * pas tant qu'on s'acharne, il expire à l'heure prévue.</p>
 *
 * <p><b>État en mémoire, sans table</b> : les compteurs vivent dans la JVM. Choix assumé tant que
 * PRS 2.0 tourne en <strong>un seul exemplaire</strong> : pas de schéma à migrer, et surtout aucune
 * écriture en base sur un chemin non authentifié — qui serait elle-même un levier de déni de
 * service. Conséquences à connaître : un redémarrage remet tous les compteurs à zéro ; et le jour
 * où l'API serait déployée en plusieurs instances derrière un répartiteur, chaque instance
 * compterait pour elle seule — il faudrait alors un stockage partagé (Redis) ou une limitation
 * portée par le répartiteur.</p>
 *
 * <p><b>Horloge</b> : injectée ({@link Clock}, bean de {@code cnm.prs.config.ClockConfig}) — seul
 * moyen de tester l'expiration d'un verrou sans faire attendre la suite quinze minutes.</p>
 */
@Component
public class LoginRateLimiter {

    /** Fenêtre glissante des échecs de connexion (les deux compteurs de login). */
    static final Duration FENETRE_LOGIN = Duration.ofMinutes(15);

    /** Échecs tolérés sur un même couple (IP, login) dans {@link #FENETRE_LOGIN}. */
    static final int ECHECS_MAX_COMPTE = 5;

    /** Échecs tolérés depuis une même IP dans {@link #FENETRE_LOGIN}, tous logins confondus. */
    static final int ECHECS_MAX_IP = 20;

    /** Fenêtre glissante des demandes d'inscription publiques. */
    static final Duration FENETRE_INSCRIPTION = Duration.ofHours(1);

    /** Demandes d'inscription tolérées depuis une même IP dans {@link #FENETRE_INSCRIPTION}. */
    static final int INSCRIPTIONS_MAX_IP = 10;

    /**
     * Au-delà de ce nombre de clés suivies, on purge celles dont la fenêtre est entièrement
     * écoulée. Sans ce ménage, une attaque distribuée ferait croître les tables indéfiniment.
     */
    private static final int SEUIL_MENAGE = 500;

    private final Clock horloge;

    /** Échecs par couple (IP, login) — clé {@code ip|login}. */
    private final Map<String, Deque<Instant>> echecsCompte = new ConcurrentHashMap<>();

    /** Échecs par IP, tous logins confondus. */
    private final Map<String, Deque<Instant>> echecsIp = new ConcurrentHashMap<>();

    /** Demandes d'inscription par IP. */
    private final Map<String, Deque<Instant>> inscriptions = new ConcurrentHashMap<>();

    public LoginRateLimiter(Clock horloge) {
        this.horloge = horloge;
    }

    /**
     * Vérifie qu'une tentative de connexion est permise. À appeler <strong>avant</strong> de
     * vérifier les identifiants : pendant un verrou, même le bon mot de passe est refusé — sans
     * quoi l'attaquant apprendrait, au changement de réponse, qu'il vient de le trouver.
     *
     * @throws TropDeRequetesException (→ HTTP 429) si le couple ou l'adresse est verrouillé
     */
    public void verifierLogin(String ip, String login) {
        Instant maintenant = horloge.instant();
        long attenteCompte = attente(echecsCompte, cle(ip, login), FENETRE_LOGIN, ECHECS_MAX_COMPTE, maintenant);
        if (attenteCompte > 0) {
            throw new TropDeRequetesException(
                    "Trop de tentatives de connexion pour cet identifiant. Réessayez dans "
                            + delai(attenteCompte) + ".", attenteCompte);
        }
        long attenteIp = attente(echecsIp, ip, FENETRE_LOGIN, ECHECS_MAX_IP, maintenant);
        if (attenteIp > 0) {
            throw new TropDeRequetesException(
                    "Trop de tentatives de connexion depuis cette adresse. Réessayez dans "
                            + delai(attenteIp) + ".", attenteIp);
        }
    }

    /** Enregistre un échec d'authentification (mot de passe faux, compte inconnu ou désactivé). */
    public void echecLogin(String ip, String login) {
        Instant maintenant = horloge.instant();
        enregistrer(echecsCompte, cle(ip, login), FENETRE_LOGIN, maintenant);
        enregistrer(echecsIp, ip, FENETRE_LOGIN, maintenant);
        menage(maintenant);
    }

    /**
     * Connexion réussie : le compteur du couple (IP, login) repart de zéro. Celui de l'IP est
     * conservé (cf. javadoc de classe).
     */
    public void succesLogin(String ip, String login) {
        echecsCompte.remove(cle(ip, login));
    }

    /**
     * Vérifie le quota d'inscriptions de l'adresse <strong>et consomme une unité</strong> : ici,
     * c'est la tentative elle-même qui est limitée, pas seulement l'échec — c'est la demande
     * <em>réussie</em> qui crée une fiche et stocke des fichiers.
     *
     * @throws TropDeRequetesException (→ HTTP 429) si le quota de l'adresse est épuisé
     */
    public void consommerInscription(String ip) {
        Instant maintenant = horloge.instant();
        long attente = attente(inscriptions, ip, FENETRE_INSCRIPTION, INSCRIPTIONS_MAX_IP, maintenant);
        if (attente > 0) {
            throw new TropDeRequetesException(
                    "Trop de demandes d'inscription depuis cette adresse. Réessayez dans "
                            + delai(attente) + ".", attente);
        }
        enregistrer(inscriptions, ip, FENETRE_INSCRIPTION, maintenant);
        menage(maintenant);
    }

    /**
     * Vide tous les compteurs. <strong>Réservé aux tests</strong> : l'état de ce bean vit hors
     * transaction, il n'est donc pas annulé entre deux tests d'intégration — sans cette remise à
     * zéro, les échecs de connexion d'une classe (toutes vues de 127.0.0.1) pollueraient les
     * suivantes jusqu'à déclencher le verrou d'IP.
     */
    public void reinitialiser() {
        echecsCompte.clear();
        echecsIp.clear();
        inscriptions.clear();
    }

    // ------------------------------------------------------------------
    // Mécanique de la fenêtre glissante
    // ------------------------------------------------------------------

    /**
     * Secondes restant avant que la clé redescende sous le seuil, ou {@code 0} si elle n'y est pas.
     * La file est triée par construction (on ajoute toujours à la fin) : après purge des évènements
     * sortis de la fenêtre, il en reste {@code n}. Il faut donc attendre l'expiration des
     * {@code n - max + 1} plus anciens, c'est-à-dire celle de l'élément d'indice {@code n - max}.
     * <p>
     * Tout se fait dans le {@code computeIfPresent} : {@link ArrayDeque} n'est pas thread-safe, et
     * c'est l'exclusion par clé de {@link ConcurrentHashMap} qui la protège ici.
     */
    private long attente(Map<String, Deque<Instant>> compteurs, String cle, Duration fenetre, int max,
            Instant maintenant) {
        Instant limite = maintenant.minus(fenetre);
        Instant[] declencheur = new Instant[1];
        compteurs.computeIfPresent(cle, (k, dates) -> {
            purger(dates, limite);
            if (dates.size() >= max) {
                declencheur[0] = elementAt(dates, dates.size() - max);
            }
            return dates.isEmpty() ? null : dates;   // null → la clé disparaît de la table
        });
        if (declencheur[0] == null) {
            return 0;
        }
        long secondes = Duration.between(maintenant, declencheur[0].plus(fenetre)).toSeconds();
        return Math.max(secondes, 1);   // jamais 0 : la clé EST verrouillée, l'appelant doit le voir
    }

    /** Ajoute un évènement daté sur la clé, après purge de ceux sortis de la fenêtre. */
    private void enregistrer(Map<String, Deque<Instant>> compteurs, String cle, Duration fenetre,
            Instant maintenant) {
        Instant limite = maintenant.minus(fenetre);
        compteurs.compute(cle, (k, dates) -> {
            Deque<Instant> file = dates == null ? new ArrayDeque<>() : dates;
            purger(file, limite);
            file.addLast(maintenant);
            return file;
        });
    }

    /** Retire du début de la file tous les évènements antérieurs à la fenêtre. */
    private static void purger(Deque<Instant> dates, Instant limite) {
        while (!dates.isEmpty() && dates.peekFirst().isBefore(limite)) {
            dates.removeFirst();
        }
    }

    /** Élément d'indice donné, l'itération d'une {@link ArrayDeque} allant du plus ancien au plus récent. */
    private static Instant elementAt(Deque<Instant> dates, int index) {
        int i = 0;
        for (Instant date : dates) {
            if (i++ == index) {
                return date;
            }
        }
        return null;   // inatteignable : index < dates.size()
    }

    /** Purge les clés dont la fenêtre est entièrement écoulée, au-delà de {@link #SEUIL_MENAGE}. */
    private void menage(Instant maintenant) {
        purgerTable(echecsCompte, maintenant.minus(FENETRE_LOGIN));
        purgerTable(echecsIp, maintenant.minus(FENETRE_LOGIN));
        purgerTable(inscriptions, maintenant.minus(FENETRE_INSCRIPTION));
    }

    private static void purgerTable(Map<String, Deque<Instant>> compteurs, Instant limite) {
        if (compteurs.size() < SEUIL_MENAGE) {
            return;
        }
        for (String cle : compteurs.keySet()) {
            compteurs.computeIfPresent(cle, (k, dates) -> {
                purger(dates, limite);
                return dates.isEmpty() ? null : dates;
            });
        }
    }

    /** Clé du compteur par couple. Le séparateur ne peut pas apparaître dans une adresse IP. */
    private static String cle(String ip, String login) {
        return ip + "|" + login;
    }

    /** Délai lisible, arrondi à la minute supérieure (« une minute », « 15 minutes »). */
    private static String delai(long secondes) {
        long minutes = (secondes + 59) / 60;
        return minutes <= 1 ? "une minute" : minutes + " minutes";
    }
}
