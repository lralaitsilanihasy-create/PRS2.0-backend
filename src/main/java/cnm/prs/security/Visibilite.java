package cnm.prs.security;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.springframework.security.access.AccessDeniedException;

import cnm.prs.enums.ProfilUtilisateur;

/**
 * Applique le périmètre de visibilité par localité (§1) aux ressources internes du circuit.
 *
 * <p>Règle commune : le Président et l'Administrateur voient tout ; les autres contrôleurs
 * ne voient que leur localité ; la PRMP (acteur externe) n'a pas accès aux ressources
 * internes (liste vide / accès refusé). Les services fournissent les requêtes « toutes » et
 * « par localité » ; cet utilitaire choisit selon le profil de l'utilisateur courant.</p>
 */
public final class Visibilite {

    private Visibilite() {
    }

    public static boolean voitTout() {
        ProfilUtilisateur p = CurrentUser.profil().orElse(null);
        return p == ProfilUtilisateur.PRESIDENT || p == ProfilUtilisateur.ADMINISTRATEUR;
    }

    /**
     * Acteur dont le périmètre est une <strong>propriété PRMP</strong> (claim {@code ref} = ID_PRMP) : la PRMP
     * <strong>ou</strong> une UGPM (dont le {@code ref} porte l'ID_PRMP de tutelle). Les deux voient et scopent
     * sur le même périmètre ; seule la <strong>soumission</strong> reste réservée à la PRMP (contrôlée à part).
     */
    public static boolean estPrmp() {
        ProfilUtilisateur p = CurrentUser.profil().orElse(null);
        return p == ProfilUtilisateur.PRMP || p == ProfilUtilisateur.UGPM;
    }

    public static Optional<String> localite() {
        return CurrentUser.localite().filter(s -> !s.isBlank());
    }

    /**
     * Liste filtrée selon le périmètre : tout (Président/Admin), rien (PRMP ou sans localité),
     * ou les entités de la localité de l'utilisateur.
     */
    public static <T> List<T> filtrer(Supplier<List<T>> tout, Function<String, List<T>> parLocalite) {
        if (voitTout()) {
            return tout.get();
        }
        if (estPrmp()) {
            return List.of();
        }
        return localite().map(parLocalite).orElseGet(List::of);
    }

    /** Vrai si l'utilisateur courant a le droit de voir une ressource (selon son périmètre). */
    public static boolean accesAutorise(Predicate<String> existsDansLocalite) {
        if (voitTout()) {
            return true;
        }
        if (estPrmp()) {
            return false;
        }
        return localite().map(existsDansLocalite::test).orElse(false);
    }

    /** Lève {@link AccessDeniedException} (→ 403) si la ressource est hors périmètre. */
    public static void controler(Predicate<String> existsDansLocalite) {
        if (!accesAutorise(existsDansLocalite)) {
            throw new AccessDeniedException("Ressource hors de votre périmètre de visibilité (§1).");
        }
    }

    /**
     * Exige que l'utilisateur agisse dans sa propre localité (§3.3) : même délégué, un
     * contrôleur ne peut agir que sur des dossiers de sa localité — sauf Président/Admin
     * (tout). Si la localité de la ressource est indéterminée ({@code null}), aucune
     * contrainte (cas d'une première réception qui établit la localité).
     */
    public static void exigerLocalite(String localiteRessource) {
        if (voitTout() || localiteRessource == null) {
            return;
        }
        if (!localite().map(localiteRessource::equals).orElse(false)) {
            throw new AccessDeniedException(
                    "Action hors de votre localité : la délégation reste limitée à votre localité (§3.3).");
        }
    }
}
