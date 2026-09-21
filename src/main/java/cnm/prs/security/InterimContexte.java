package cnm.prs.security;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * ⚠️ Intérim désigné (lot 1, 2026-09-21) — les <strong>suppléances actives du connecté</strong>, posées
 * <strong>une fois par requête</strong> (par {@code InterimContexteInterceptor}, après l'authentification)
 * et lues par toutes les gardes qui raisonnent sur l'identité ou le périmètre : {@link Visibilite},
 * {@link PermissionService}, et les services du circuit.
 *
 * <p><strong>Pourquoi un contexte de requête et non un appel par garde.</strong> La garde centrale
 * « titulaire OU délégation OU intérimaire actif » est évaluée à chaque {@code @PreAuthorize}, et le
 * périmètre de visibilité ({@code Visibilite.voitTout}) en 77 endroits : le CC intérimaire du Président
 * doit VOIR toutes les localités pendant l'intérim (§B4.1 de la demande). Interroger la base à chacun de
 * ces points aurait multiplié les requêtes et les oublis ; les résoudre une fois, au seuil de la
 * requête, donne à toutes les gardes la même réponse au même instant.</p>
 *
 * <p>Même modèle que {@link CurrentUser} : utilitaire statique, aucune injection. Hors requête HTTP (tâche
 * de fond, test appelant un service en direct), le contexte est vide : aucune suppléance, donc aucun droit
 * étendu — jamais une exception. Le fil d'exécution est libéré en {@code afterCompletion}.</p>
 */
public final class InterimContexte {

    private static final ThreadLocal<List<Suppleance>> SUPPLEANCES = new ThreadLocal<>();

    private InterimContexte() {
    }

    /** Pose les suppléances actives du connecté pour la requête en cours ({@code null} vaut aucune). */
    public static void poser(List<Suppleance> suppleances) {
        SUPPLEANCES.set(suppleances == null ? List.of() : List.copyOf(suppleances));
    }

    /** Libère le fil d'exécution — à appeler en fin de requête, quoi qu'il arrive. */
    public static void effacer() {
        SUPPLEANCES.remove();
    }

    /** Les suppléances actives du connecté ; vide hors requête ou sans intérim. Jamais {@code null}. */
    public static List<Suppleance> suppleances() {
        List<Suppleance> s = SUPPLEANCES.get();
        return s == null ? List.of() : s;
    }

    /** Vrai si le connecté agit au nom du Président : son périmètre devient toutes les localités. */
    public static boolean suppleeLePresident() {
        return suppleances().stream().anyMatch(Suppleance::duPresident);
    }

    /** La suppléance au nom du Président, s'il y en a une. */
    public static Optional<Suppleance> duPresident() {
        return suppleances().stream().filter(Suppleance::duPresident).findFirst();
    }

    /** La suppléance du connecté pour ce titulaire précis, s'il le supplée aujourd'hui. */
    public static Optional<Suppleance> pour(String imTitulaire) {
        if (imTitulaire == null || imTitulaire.isBlank()) {
            return Optional.empty();
        }
        return suppleances().stream().filter(s -> imTitulaire.equals(s.imTitulaire())).findFirst();
    }

    /**
     * Les <strong>identités</strong> sous lesquelles le connecté peut être reconnu par une garde nominative :
     * la sienne, plus celle de chaque titulaire qu'il supplée aujourd'hui. C'est l'argument des surcharges
     * « par identités » de {@code PredicatsIdentite} — « le dispatcheur » devient « le dispatcheur ou son
     * intérimaire actif » sans qu'aucune garde ne réécrive la règle.
     */
    public static Set<String> identites(String moi) {
        Set<String> identites = new LinkedHashSet<>();
        if (moi != null && !moi.isBlank()) {
            identites.add(moi);
        }
        for (Suppleance s : suppleances()) {
            identites.add(s.imTitulaire());
        }
        return identites;
    }
}
