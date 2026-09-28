package cnm.prs.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ⚠️ <strong>Le moteur des conditions déclarées</strong> des fichiers de commande (lot D, demande front du 2026-09-28 §B1 ;
 * ADR-0011) — les sections {@code {{SI:NOM}}} … {@code {{FINSI:NOM}}} d'un document type rempli ne sont plus choisies par
 * des noms écrits en dur : chaque nom est <strong>déclaré en tête du fichier</strong> ({@code CONDITION<TAB>NOM<US>expression})
 * et son expression est évaluée sur la fiche figée. Pur : une expression et un lecteur de valeurs en entrée, un booléen en
 * sortie.
 *
 * <p><strong>Grammaire</strong> — celle des conditions du référentiel ({@link ConditionCadrage}), étendue :</p>
 * <ul>
 *   <li>termes {@code cle = valeur}, {@code cle != valeur} (la valeur court jusqu'à la fin du terme), {@code cle contient
 *       texte}, {@code cle renseigne}, {@code cle vide} ;</li>
 *   <li>reliés par {@code et} (prioritaire) puis {@code ou}, sans parenthèses. Un {@code et} / {@code ou} ne sépare deux
 *       termes que s'il est suivi d'un terme complet (clé puis opérateur) : une valeur peut contenir « et » (« Au fur et à
 *       mesure des besoins ») ;</li>
 *   <li>comparaisons sans casse, blancs réduits, apostrophes droites et courbes confondues ; {@code =} est faux sur une
 *       valeur absente, {@code !=} vrai.</li>
 * </ul>
 * <p>Une {@code cle} est un <strong>code de champ</strong> ({@code B07-FS-01}) ou une <strong>clé de cadrage</strong>
 * ({@code attributaires}, {@code modeRemise}…) : c'est le lecteur fourni qui la résout (voir {@link FormulairesCandidat}).</p>
 */
public final class ConditionsModele {

    /** Une clé : un code de champ, ou un identifiant (clé de cadrage). */
    static final String CLE = "(?:B\\d{2}-[A-Z0-9]{1,6}-\\d{2}|[A-Za-z_][A-Za-z0-9_]*)";
    private static final String DEBUT_TERME = "(?=" + CLE + "(?:\\s*!?=|\\s+contient\\s|\\s+(?:renseigne|vide)(?:\\s|$)))";
    private static final Pattern OU = Pattern.compile("\\s+ou\\s+" + DEBUT_TERME);
    private static final Pattern ET = Pattern.compile("\\s+et\\s+" + DEBUT_TERME);
    private static final Pattern EGAL = Pattern.compile("^(" + CLE + ")\\s*(!=|=)\\s*(.+)$");
    private static final Pattern CONTIENT = Pattern.compile("^(" + CLE + ")\\s+contient\\s+(.+)$");
    private static final Pattern ETAT = Pattern.compile("^(" + CLE + ")\\s+(renseigne|vide)$");
    private static final Pattern MARQUEUR = Pattern.compile("\\{\\{(SI|FINSI):([A-Z0-9-]+)}}");

    private ConditionsModele() {
    }

    /** L'expression se lit-elle (chaque terme reconnu) ? */
    public static boolean lisible(String expression) {
        if (expression == null || expression.isBlank()) {
            return false;
        }
        for (String alternative : OU.split(expression.trim())) {
            for (String terme : ET.split(alternative.trim())) {
                String t = terme.trim();
                if (!EGAL.matcher(t).matches() && !CONTIENT.matcher(t).matches() && !ETAT.matcher(t).matches()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * L'expression est-elle vraie ? {@code lecteur} rend la valeur brute d'une clé ({@code null} : absente).
     *
     * @throws IllegalArgumentException si un terme ne se lit pas
     */
    public static boolean vraie(String expression, Function<String, String> lecteur) {
        for (String alternative : OU.split(expression.trim())) {
            boolean toutes = true;
            for (String terme : ET.split(alternative.trim())) {
                if (!terme(terme.trim(), lecteur)) {
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

    /**
     * ⚠️ Import du DAO (2026-09-28, §B1, ADR-0012) — les réponses qu'<strong>implique</strong> une section retenue : chaque
     * terme {@code cle = valeur} d'une conjonction, dans l'ordre. Rien pour une expression à alternatives ({@code ou}), et
     * les termes {@code !=}, {@code contient}, {@code renseigne}, {@code vide} n'impliquent rien. Même découpage que
     * {@link #vraie} : un « et » dans une valeur n'est pas un séparateur.
     */
    public static List<Map.Entry<String, String>> implications(String expression) {
        if (expression == null || expression.isBlank()) {
            return List.of();
        }
        String[] alternatives = OU.split(expression.trim());
        if (alternatives.length != 1) {
            return List.of();
        }
        List<Map.Entry<String, String>> out = new java.util.ArrayList<>();
        for (String terme : ET.split(alternatives[0].trim())) {
            Matcher m = EGAL.matcher(terme.trim());
            if (m.matches() && "=".equals(m.group(2))) {
                out.add(Map.entry(m.group(1), m.group(3).trim()));
            }
        }
        return out;
    }

    private static boolean terme(String terme, Function<String, String> lecteur) {
        Matcher m = EGAL.matcher(terme);
        if (m.matches()) {
            String v = normaliser(lecteur.apply(m.group(1)));
            boolean egal = !v.isEmpty() && v.equals(normaliser(m.group(3)));
            return "=".equals(m.group(2)) ? egal : !egal;
        }
        m = CONTIENT.matcher(terme);
        if (m.matches()) {
            String v = normaliser(lecteur.apply(m.group(1)));
            return !v.isEmpty() && v.contains(normaliser(m.group(2)));
        }
        m = ETAT.matcher(terme);
        if (m.matches()) {
            boolean renseigne = !normaliser(lecteur.apply(m.group(1))).isEmpty();
            return "renseigne".equals(m.group(2)) ? renseigne : !renseigne;
        }
        throw new IllegalArgumentException("Terme de condition illisible : « " + terme + " ».");
    }

    /** Casse, blancs, apostrophes : la forme sous laquelle deux textes se comparent. */
    static String normaliser(String s) {
        if (s == null) {
            return "";
        }
        return s.replace('’', '\'').replace('‘', '\'').replace(' ', ' ').trim()
                .replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /**
     * Contrôle d'un modèle au chargement : chaque condition déclarée se lit, chaque section {@code SI} / {@code FINSI}
     * utilisée (paragraphes et cellules) est déclarée — ou fait partie de {@code admises} (les noms historiques des
     * formulaires du candidat) —, et les sections s'emboîtent (chaque {@code FINSI} ferme la dernière {@code SI} ouverte).
     *
     * @return la liste des défauts, vide si le modèle est sain
     */
    public static List<String> defauts(List<DocumentLibre.Element> elements, Map<String, String> conditions, Set<String> admises) {
        List<String> defauts = new ArrayList<>();
        conditions.forEach((nom, expression) -> {
            if (!lisible(expression)) {
                defauts.add("condition " + nom + " illisible : « " + expression + " »");
            }
        });
        Deque<String> pile = new ArrayDeque<>();
        Set<String> utilisees = new LinkedHashSet<>();
        for (String texte : textes(elements)) {
            Matcher m = MARQUEUR.matcher(texte);
            while (m.find()) {
                String nom = m.group(2);
                utilisees.add(nom);
                if ("SI".equals(m.group(1))) {
                    pile.push(nom);
                } else if (!nom.equals(pile.peek())) {
                    defauts.add("FINSI:" + nom + " ne ferme pas la dernière section ouverte (" + pile.peek() + ")");
                } else {
                    pile.pop();
                }
            }
        }
        if (!pile.isEmpty()) {
            defauts.add("section(s) non refermée(s) : " + String.join(", ", pile));
        }
        for (String nom : utilisees) {
            if (!conditions.containsKey(nom) && !admises.contains(nom)) {
                defauts.add("condition " + nom + " utilisée sans être déclarée");
            }
        }
        return defauts;
    }

    /** Les textes d'un modèle, dans l'ordre : paragraphes, puis chaque paragraphe de chaque cellule. */
    private static List<String> textes(List<DocumentLibre.Element> elements) {
        List<String> out = new ArrayList<>();
        for (DocumentLibre.Element e : elements) {
            if (e instanceof DocumentLibre.Paragraphe p) {
                out.add(p.texte());
            } else if (e instanceof DocumentLibre.Tableau t) {
                t.lignes().forEach(l -> l.forEach(out::addAll));
            }
        }
        return out;
    }

    /** Les conditions déclarées d'un fichier, dans l'ordre ({@code NOM → expression}). */
    public static Map<String, String> vides() {
        return new LinkedHashMap<>();
    }
}
