package cnm.prs.service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ⚠️ Fiche marché (2026-09-22, §B1) — <strong>condition d'affichage</strong> d'un champ, expression sur le cadrage.
 *
 * <p>Grammaire, volontairement minuscule, <strong>identique à l'évaluateur du front</strong>
 * ({@code features/prmp/fiche-marche/fiche-marche-modele.ts}, {@code evaluerCondition}) — à faire évoluer ensemble :</p>
 * <ol>
 *   <li>la chaîne est coupée sur {@code ou} (casse ignorée, entouré d'espaces) : <em>un groupe vrai suffit</em> ;</li>
 *   <li>chaque groupe est coupé sur {@code et} : <em>tous les termes doivent être vrais</em> ;</li>
 *   <li>un terme est {@code cle = VALEUR} ou {@code cle != VALEUR}
 *       ({@code ^([A-Za-z_][A-Za-z0-9_]*)\s*(=|!=)\s*([A-Za-z0-9_]+)$}) ; la clé se lit dans le cadrage, casse ignorée
 *       pour la valeur ; <strong>clé absente ⇒ chaîne vide</strong> (donc {@code = X} faux, {@code != X} vrai) ;</li>
 *   <li>un terme illisible vaut <strong>faux</strong>, jamais une exception ; une condition nulle ou vide vaut
 *       <strong>vrai</strong>.</li>
 * </ol>
 *
 * <p>Pure : aucune lecture de base. Les valeurs comparables sont des codes ({@code OUI}, {@code UNITAIRES}…) ;
 * les compléments numériques se comparent comme chaînes décimales — les seuils sont des règles B4, pas des conditions.</p>
 */
public final class ConditionCadrage {

    /** Un terme : clé, opérateur, valeur — le même motif que le front. */
    public static final Pattern TERME = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)\\s*(=|!=)\\s*([A-Za-z0-9_]+)$");

    private ConditionCadrage() {
    }

    public static boolean vraie(String condition, Map<String, ?> cadrage) {
        if (condition == null || condition.isBlank()) {
            return true;
        }
        for (String alternative : condition.trim().split("(?i)\\s+ou\\s+")) {
            boolean toutes = true;
            for (String terme : alternative.split("(?i)\\s+et\\s+")) {
                if (!terme(terme.trim(), cadrage)) {
                    toutes = false;
                    break;
                }
            }
            if (toutes) {
                return true;
            }
        }
        return false;
    }

    /** Vrai si chaque terme de la condition se lit (contrôle à l'écriture du référentiel). */
    public static boolean lisible(String condition) {
        if (condition == null || condition.isBlank()) {
            return true;
        }
        for (String alternative : condition.trim().split("(?i)\\s+ou\\s+")) {
            for (String terme : alternative.split("(?i)\\s+et\\s+")) {
                if (!TERME.matcher(terme.trim()).matches()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean terme(String terme, Map<String, ?> cadrage) {
        Matcher m = TERME.matcher(terme);
        if (!m.matches()) {
            return false;
        }
        Object brut = cadrage == null ? null : cadrage.get(m.group(1));
        String reponse = brut == null ? "" : String.valueOf(brut).trim();
        boolean egal = reponse.equalsIgnoreCase(m.group(3));
        return "!=".equals(m.group(2)) != egal;
    }
}
