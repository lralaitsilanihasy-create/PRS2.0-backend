package cnm.prs.enums;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3) — <strong>catégorie de prestations</strong> de
 * l'arrêté n° 13 156/2019-MEF, qui choisit la ligne du barème de seuils.
 *
 * <p>C'est le champ qui manquait (plan, §4, lot 3, 3.g, point 2) : l'arrêté fixe ses seuils par
 * catégorie, PRS ne connaît que trois natures ({@code Travaux}, {@code Fournitures}, {@code Services}).
 * Avec la seule nature, impossible de dire quel seuil s'applique à un marché de travaux — de 20 millions
 * à 5 milliards selon qu'il s'agit d'une route, d'un entretien routier ou d'autre chose.</p>
 *
 * <p>La catégorie est portée <strong>par la ligne</strong> ({@code t_marche.CATEGORIE_SEUIL}) et reste
 * <strong>facultative</strong> : une ligne qui n'en porte pas n'est pas en faute. Le pré-contrôle évalue
 * alors les catégories {@link #plausiblesPourNature plausibles} de sa nature et ne signale que si elles
 * divergent — c'est la parade à la fatigue d'alerte (3.e) : tant que toutes les lectures donnent la même
 * réponse, il n'y a rien à demander à la PRMP.</p>
 */
public enum CategorieSeuil {

    /** Travaux de construction ou de réhabilitation des routes. */
    ROUTES_CONSTRUCTION,

    /** Travaux d'entretien routier. */
    ENTRETIEN_ROUTIER,

    /** Travaux non routiers (bâtiments, aménagement hydro-agricole, autres). */
    TRAVAUX_NON_ROUTIERS,

    /** Fournitures et services courants. */
    FOURNITURES_SERVICES,

    /**
     * Prestations intellectuelles. Elles partagent le seuil de <strong>contrôle</strong> des fournitures
     * et services (300 M / 150 M) mais n'ont <strong>pas</strong> de seuil de procédure dans l'arrêté :
     * leur publicité relève du décret n° 2019-1310 (appel à manifestation d'intérêt).
     */
    PRESTATIONS_INTELLECTUELLES;

    /** Les trois catégories de travaux — celles que la nature « Travaux » ne permet pas de départager. */
    private static final List<CategorieSeuil> TRAVAUX =
            List.of(ROUTES_CONSTRUCTION, ENTRETIEN_ROUTIER, TRAVAUX_NON_ROUTIERS);

    /**
     * Catégories <strong>plausibles</strong> pour un libellé de nature, quand la ligne n'en précise
     * aucune. Rapprochement sur le libellé normalisé de {@code tr_nature} (les natures sont un
     * référentiel administrable, pas une énumération) :
     *
     * <ul>
     *   <li>« Travaux » → les trois catégories de travaux ;</li>
     *   <li>« Fournitures » → fournitures et services, seule lecture possible ;</li>
     *   <li>« Services » → fournitures et services <em>ou</em> prestations intellectuelles — une
     *       prestation intellectuelle est un service, et PRS n'a pas de nature pour la distinguer ;</li>
     *   <li>nature inconnue ou absente → toutes, ce qui revient à ne rien affirmer.</li>
     * </ul>
     */
    public static List<CategorieSeuil> plausiblesPourNature(String libelleNature) {
        String n = normaliser(libelleNature);
        if (n.startsWith("travaux")) {
            return TRAVAUX;
        }
        if (n.startsWith("fourniture")) {
            return List.of(FOURNITURES_SERVICES);
        }
        if (n.startsWith("service") || n.startsWith("prestation")) {
            return List.of(FOURNITURES_SERVICES, PRESTATIONS_INTELLECTUELLES);
        }
        return List.of(values());
    }

    /** Minuscules sans accents — les libellés de nature viennent aussi bien de la saisie que d'un PDF. */
    private static String normaliser(String libelle) {
        return libelle == null ? "" : Normalizer.normalize(libelle, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").trim().toLowerCase(Locale.FRENCH);
    }
}
